package com.adn.dabaeum.common.api;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode code;
    private final List<String> details;

    public ApiException(
        HttpStatus status,
        ApiErrorCode code,
        String message
    ) {
        this(status, code, message, List.of());
    }

    public ApiException(
        HttpStatus status,
        ApiErrorCode code,
        String message,
        List<String> details
    ) {
        super(Objects.requireNonNull(message, "message"));
        this.status = Objects.requireNonNull(status, "status");
        this.code = Objects.requireNonNull(code, "code");
        this.details = List.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode code() {
        return code;
    }

    public List<String> details() {
        return details;
    }
}
