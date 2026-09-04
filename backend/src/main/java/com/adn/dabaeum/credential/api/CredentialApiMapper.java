package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.application.CredentialPage;
import com.adn.dabaeum.credential.application.CredentialVerifyCommand;
import com.adn.dabaeum.credential.application.CredentialVerificationPage;
import com.adn.dabaeum.credential.application.IssueCredentialCommand;
import com.adn.dabaeum.credential.application.ListUserCredentialsQuery;
import com.adn.dabaeum.credential.application.ReissueCredentialCommand;
import com.adn.dabaeum.credential.application.RevokeCredentialCommand;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialView;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

@Component
public class CredentialApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "issuedAt,asc", "issuedAt,desc",
        "updatedAt,asc", "updatedAt,desc", "verifiedAt,asc", "verifiedAt,desc");
    private static final Set<String> VERIFICATION_TYPES = Set.of("QR", "API", "ADMIN");
    private static final Set<String> REQUESTER_TYPES = Set.of(
        "INDIVIDUAL", "INSTITUTION", "EXTERNAL_ORGANIZATION", "SYSTEM");
    private static final Set<String> VERIFICATION_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "verifiedAt,asc", "verifiedAt,desc");
    private static final Pattern HASH = Pattern.compile("^[0-9a-fA-F]{64}$");
    private final Clock clock;

    public CredentialApiMapper(Clock clock) {
        this.clock = clock;
    }

    public IssueCredentialCommand toIssueCommand(
        UUID completionId,
        CredentialIssueRequest request,
        String idempotencyKey,
        AuthenticatedUserContext actor
    ) {
        Instant validUntil = parseValidUntil(request == null ? null : request.validUntil());
        return new IssueCredentialCommand(completionId, validUntil, idempotencyKey, actor,
            clock.instant());
    }

    public ListUserCredentialsQuery toListQuery(
        UUID userId,
        int page,
        int size,
        String sort,
        AuthenticatedUserContext actor
    ) {
        if (page < 0 || size < 1 || size > 100 || sort == null || sort.isBlank()
            || page > Integer.MAX_VALUE / size
            || !ALLOWED_SORTS.contains(sort.trim())) {
            throw new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid",
                List.of("page", "size", "sort"));
        }
        return new ListUserCredentialsQuery(userId, page, size, sort, actor);
    }

    public RevokeCredentialCommand toRevokeCommand(
        UUID credentialId, CredentialRevokeRequest request, String idempotencyKey,
        AuthenticatedUserContext actor
    ) {
        return new RevokeCredentialCommand(credentialId, requireReason(request == null ? null : request.reason()),
            idempotencyKey, actor, clock.instant());
    }

    public ReissueCredentialCommand toReissueCommand(
        UUID credentialId, CredentialReissueRequest request, String idempotencyKey,
        AuthenticatedUserContext actor
    ) {
        return new ReissueCredentialCommand(credentialId,
            requireReason(request == null ? null : request.reason()),
            parseValidUntil(request == null ? null : request.validUntil()), idempotencyKey, actor,
            clock.instant());
    }

    public CredentialVerifyCommand toVerifyCommand(
        CredentialVerifyRequest request, String requestId
    ) {
        if (request == null) {
            throw invalidVerification("requestBody");
        }
        String credentialNo = text(request.credentialNo());
        String credentialHash = text(request.credentialHash());
        if ((credentialNo == null) == (credentialHash == null)) {
            throw invalidVerification("credentialNo", "credentialHash");
        }
        if (credentialNo != null && credentialNo.length() > 100) {
            throw invalidVerification("credentialNo");
        }
        if (credentialHash != null && !HASH.matcher(credentialHash).matches()) {
            throw invalidVerification("credentialHash");
        }
        if (credentialHash != null) {
            credentialHash = credentialHash.toLowerCase(Locale.ROOT);
        }
        String verificationType = text(request.verificationType());
        String requesterType = text(request.requesterType());
        // Set.of(...) 는 contains(null) 에서 NullPointerException 을 던진다.
        // 필수 필드를 빠뜨린 요청이 400 대신 500 으로 나가지 않도록 null 을 먼저 거른다.
        if (verificationType == null || !VERIFICATION_TYPES.contains(verificationType)) {
            throw invalidVerification("verificationType");
        }
        if (requesterType == null || !REQUESTER_TYPES.contains(requesterType)) {
            throw invalidVerification("requesterType");
        }
        return new CredentialVerifyCommand(credentialNo, credentialHash, verificationType,
            requesterType, requestId, clock.instant());
    }

    public CredentialVerificationPageQuery toVerificationPageQuery(
        int page, int size, String sort
    ) {
        if (page < 0 || size < 1 || size > 100 || sort == null || sort.isBlank()
            || page > Integer.MAX_VALUE / size || !VERIFICATION_SORTS.contains(sort.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size", "sort"));
        }
        return new CredentialVerificationPageQuery(page, size, sort.trim());
    }

    public ApiResponse<CredentialResponse> toApiResponse(Credential credential, String requestId) {
        return new ApiResponse<>(toResponse(credential), meta(requestId));
    }

    public CredentialResponse toResponse(Credential credential) {
        return new CredentialResponse(
            credential.id(), credential.credentialGroupId(), credential.previousCredentialId(),
            credential.credentialNo(), credential.versionNo(), credential.issuerIdentifier(),
            credential.subjectIdentifier(), credential.credentialType(), credential.status(),
            credential.validFrom(), credential.validUntil(), credential.vcHash(),
            credential.issuedAt(), credential.revokedAt(), credential.revocationReason(),
            credential.createdAt(), credential.updatedAt());
    }

    public ApiResponse<CredentialDetailResponse> toDetailApiResponse(
        CredentialView view, String requestId
    ) {
        return new ApiResponse<>(toDetailResponse(view), meta(requestId));
    }

    public CredentialDetailResponse toDetailResponse(CredentialView view) {
        Credential credential = view.credential();
        CredentialCourseView course = view.course();
        return new CredentialDetailResponse(
            credential.id(), credential.credentialGroupId(), credential.previousCredentialId(),
            credential.credentialNo(), credential.versionNo(), credential.issuerIdentifier(),
            credential.subjectIdentifier(), credential.credentialType(), credential.status(),
            credential.validFrom(), credential.validUntil(), credential.vcHash(),
            credential.issuedAt(), credential.revokedAt(), credential.revocationReason(),
            credential.createdAt(), credential.updatedAt(),
            course == null ? null : course.courseId(),
            course == null ? null : course.courseTitle(),
            course == null ? null : course.courseCode(),
            course == null ? null : course.institutionName());
    }

    public CredentialPageResponse toPageResponse(CredentialPage page, String requestId) {
        return new CredentialPageResponse(
            page.data().stream().map(this::toDetailResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public ApiResponse<CredentialVerificationResponse> toVerificationResponse(
        CredentialVerification verification, String requestId
    ) {
        return new ApiResponse<>(toVerificationResponse(verification), meta(requestId));
    }

    public CredentialVerificationResponse toVerificationResponse(
        CredentialVerification verification
    ) {
        return new CredentialVerificationResponse(
            verification.id(), verification.credentialId(), verification.presentedCredentialNo(),
            verification.verificationType(), verification.requesterType(), verification.requesterId(),
            verification.result(), verification.verifiedAt(), verification.createdAt());
    }

    public CredentialVerificationPageResponse toVerificationPageResponse(
        CredentialVerificationPage page, String requestId
    ) {
        return new CredentialVerificationPageResponse(
            page.data().stream().map(this::toVerificationResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private Instant parseValidUntil(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw invalidDate();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidDate();
        }
    }

    private String requireReason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 1000) {
            throw new ApiException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("reason"));
        }
        return value.trim();
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException invalidVerification(String... fields) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(fields));
    }

    public record CredentialVerificationPageQuery(int page, int size, String sort) {
    }

    private ApiException invalidDate() {
        return new ApiException(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of("validUntil"));
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
