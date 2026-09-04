package com.adn.dabaeum.common.api;

import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
        ApiException exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return respond(
            exception.status(),
            exception.code(),
            exception.getMessage(),
            exception.details(),
            request,
            response
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
        HttpMessageNotReadableException exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (containsEnumFormatFailure(exception)) {
            return respond(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                List.of("requestBody"),
                request,
                response
            );
        }

        return respond(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Malformed request body",
            List.of("requestBody"),
            request,
            response
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return respond(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid",
            List.of(exception.getName()),
            request,
            response
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidArgument(
        MethodArgumentNotValidException exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        List<String> details = exception.getBindingResult().getFieldErrors()
            .stream()
            .map(error -> error.getField())
            .distinct()
            .toList();

        return respond(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            details.isEmpty() ? List.of("requestBody") : details,
            request,
            response
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        List<String> details = exception.getConstraintViolations()
            .stream()
            .map(violation -> violation.getPropertyPath().toString())
            .distinct()
            .toList();

        return respond(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid",
            details.isEmpty() ? List.of("request") : details,
            request,
            response
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
        HandlerMethodValidationException exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return respond(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid",
            List.of("request"),
            request,
            response
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
        Exception exception,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        // 응답은 코드와 requestId 만 담으므로, 기록하지 않으면 원인이 어디에도 남지 않는다.
        // 응답 본문은 그대로 두고 서버 로그에만 남겨 requestId 로 추적할 수 있게 한다.
        LOGGER.error("Unhandled API failure: requestId={}, method={}, path={}",
            request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            request.getMethod(), request.getRequestURI(), exception);
        return respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCode.INTERNAL_SERVER_ERROR,
            "Internal server error",
            List.of(),
            request,
            response
        );
    }

    private ResponseEntity<ApiErrorResponse> respond(
        HttpStatus status,
        ApiErrorCode code,
        String message,
        List<String> details,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String requestId = requestId(request);
        response.setHeader(RequestIdFilter.HEADER_NAME, requestId);
        ApiErrorResponse body = new ApiErrorResponse(
            code.name(),
            message,
            List.copyOf(details),
            requestId,
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)
        );
        return ResponseEntity.status(status).body(body);
    }

    private String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }

        String header = request.getHeader(RequestIdFilter.HEADER_NAME);
        if (header != null && !header.isBlank()) {
            return header;
        }

        String requestId = UUID.randomUUID().toString();
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, requestId);
        return requestId;
    }

    private boolean containsEnumFormatFailure(Throwable exception) {
        return Stream.iterate(
                exception,
                current -> current != null,
                Throwable::getCause
            )
            .anyMatch(this::isEnumFormatFailure);
    }

    private boolean isEnumFormatFailure(Throwable exception) {
        return exception instanceof InvalidFormatException formatException
            && formatException.getTargetType() != null
            && formatException.getTargetType().isEnum();
    }
}
