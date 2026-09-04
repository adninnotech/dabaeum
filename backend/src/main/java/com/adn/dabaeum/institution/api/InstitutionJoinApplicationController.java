package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.institution.application.ApplyInstitutionJoinCommand;
import com.adn.dabaeum.institution.application.InstitutionJoinApplicationPage;
import com.adn.dabaeum.institution.application.InstitutionJoinApplicationService;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class InstitutionJoinApplicationController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final InstitutionJoinApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public InstitutionJoinApplicationController(
        InstitutionJoinApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @PostMapping("/institution-applications")
    public ResponseEntity<ApiResponse<InstitutionJoinApplicationResponse>> apply(
        @RequestBody(required = false) InstitutionJoinApplicationCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        InstitutionJoinApplication application = service.apply(
            toCommand(request, currentUserProvider.requireContext()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create(
                "/api/v1/institution-applications/" + application.id()))
            .body(new ApiResponse<>(
                toResponse(application), meta(requestId(servletRequest))));
    }

    @GetMapping("/institution-applications")
    public InstitutionJoinApplicationPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        validatePaging(page, size);
        InstitutionJoinApplicationPage result = service.list(
            currentUserProvider.requireContext(), parseStatus(status), page, size);
        return new InstitutionJoinApplicationPageResponse(
            result.data().stream().map(this::toResponse).toList(),
            new PageMeta(result.page(), result.size(),
                result.totalElements(), result.totalPages()),
            meta(requestId(servletRequest)));
    }

    @GetMapping("/institution-applications/{applicationId}")
    public ApiResponse<InstitutionJoinApplicationResponse> get(
        @PathVariable UUID applicationId,
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            toResponse(service.get(applicationId, currentUserProvider.requireContext())),
            meta(requestId(servletRequest)));
    }

    @PostMapping("/institution-applications/{applicationId}/approve")
    public ApiResponse<InstitutionJoinApplicationResponse> approve(
        @PathVariable UUID applicationId,
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            toResponse(service.approve(
                applicationId, currentUserProvider.requireContext())),
            meta(requestId(servletRequest)));
    }

    @PostMapping("/institution-applications/{applicationId}/reject")
    public ApiResponse<InstitutionJoinApplicationResponse> reject(
        @PathVariable UUID applicationId,
        @RequestBody(required = false) InstitutionJoinApplicationRejectRequest request,
        HttpServletRequest servletRequest
    ) {
        String reason = request == null || request.rejectionReason() == null
            ? null : request.rejectionReason().trim();
        if (reason == null || reason.isEmpty() || reason.length() > 1000) {
            throw validation(List.of("rejectionReason"));
        }
        return new ApiResponse<>(
            toResponse(service.reject(
                applicationId, reason, currentUserProvider.requireContext())),
            meta(requestId(servletRequest)));
    }

    private ApplyInstitutionJoinCommand toCommand(
        InstitutionJoinApplicationCreateRequest request,
        com.adn.dabaeum.common.security.AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of(
                "institutionName", "representativeName", "contactEmail", "contactPhone"));
        }
        List<String> invalid = new ArrayList<>();
        String institutionName = trimmed(request.institutionName());
        String representativeName = trimmed(request.representativeName());
        String contactEmail = trimmed(request.contactEmail());
        String contactPhone = trimmed(request.contactPhone());
        String institutionCode = trimmed(request.institutionCode());
        String address = trimmed(request.address());
        if (institutionName == null || institutionName.length() > 200) {
            invalid.add("institutionName");
        }
        if (representativeName == null || representativeName.length() > 100) {
            invalid.add("representativeName");
        }
        if (contactEmail == null || contactEmail.length() > 255
            || !contactEmail.contains("@")) {
            invalid.add("contactEmail");
        }
        if (contactPhone == null || contactPhone.length() > 50) {
            invalid.add("contactPhone");
        }
        if (institutionCode != null && institutionCode.length() > 50) {
            invalid.add("institutionCode");
        }
        if (address != null && address.length() > 500) {
            invalid.add("address");
        }
        if (!invalid.isEmpty()) {
            throw validation(invalid);
        }
        return new ApplyInstitutionJoinCommand(
            institutionName, institutionCode, representativeName,
            contactEmail, contactPhone, address, actor, clock.instant());
    }

    private InstitutionJoinApplicationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InstitutionJoinApplicationStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("status"));
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
    }

    private InstitutionJoinApplicationResponse toResponse(
        InstitutionJoinApplication application
    ) {
        return new InstitutionJoinApplicationResponse(
            application.id(), application.institutionName(),
            application.institutionCode(), application.representativeName(),
            application.contactEmail(), application.contactPhone(),
            application.address(), application.status(),
            application.rejectionReason(), application.createdInstitutionId(),
            application.decidedAt(), application.createdAt());
    }

    private String trimmed(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private ApiException validation(List<String> fields) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", fields);
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
