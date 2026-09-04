package com.adn.dabaeum.authorization.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 초기 Stage 3 역할 관리 인가 규칙을 한곳에서 관리한다.
 */
@Component
public final class AuthorizationPolicy {

    public void requirePlatformAdmin(AuthenticatedUserContext context) {
        if (context == null || context.roles().stream().noneMatch(role ->
            "PLATFORM_ADMIN".equals(role.role())
                && role.institutionId() == null
        )) {
            throw forbidden();
        }
    }

    public void requirePlatformAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.UNAUTHORIZED,
                "Authentication required"
            );
        }
        boolean platformAdmin = authentication.getAuthorities().stream()
            .anyMatch(authority -> Objects.equals(
                authority.getAuthority(),
                "ROLE_PLATFORM_ADMIN"
            ));
        if (!platformAdmin) {
            throw forbidden();
        }
    }

    public void requireCourseManager(
        AuthenticatedUserContext context,
        UUID institutionId
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.UNAUTHORIZED,
                "Authentication required"
            );
        }
        if (institutionId == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                java.util.List.of("institutionId")
            );
        }

        boolean allowed = context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role())
                && role.institutionId() == null)
                || ("INSTITUTION_ADMIN".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
        );
        if (!allowed) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed"
            );
        }
    }

    public void requireCourseSessionManager(
        AuthenticatedUserContext context,
        UUID institutionId,
        boolean mainInstructorAssigned
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.UNAUTHORIZED,
                "Authentication required"
            );
        }
        if (institutionId == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                java.util.List.of("institutionId")
            );
        }

        boolean allowed = context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null)
                || ("INSTITUTION_ADMIN".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
                || ("INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId)
                    && mainInstructorAssigned)
        );
        if (!allowed) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed"
            );
        }
    }

    public void requireEnrollmentReader(
        AuthenticatedUserContext context,
        UUID institutionId,
        boolean courseInstructorAssigned
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (institutionId == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", java.util.List.of("institutionId"));
        }
        boolean allowed = context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null)
                || ("INSTITUTION_ADMIN".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
                || ("INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId)
                    && courseInstructorAssigned));
        if (!allowed) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed");
        }
    }

    public void requireEnrollmentSubjectOrReader(
        AuthenticatedUserContext context,
        UUID subjectUserId,
        UUID institutionId,
        boolean courseInstructorAssigned
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (Objects.equals(context.userId(), subjectUserId)) {
            return;
        }
        requireEnrollmentReader(context, institutionId, courseInstructorAssigned);
    }

    public void requireCredentialSubjectOrInstitutionReader(
        AuthenticatedUserContext context,
        UUID subjectUserId,
        UUID institutionId
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (Objects.equals(context.userId(), subjectUserId)) {
            return;
        }
        requireCourseManager(context, institutionId);
    }

    public void requireCredentialListReader(
        AuthenticatedUserContext context,
        List<UUID> institutionIds
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.UNAUTHORIZED,
                "Authentication required"
            );
        }
        boolean platformAdmin = context.roles().stream().anyMatch(role ->
            "PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null);
        boolean institutionAdmin = context.roles().stream().anyMatch(role ->
            "INSTITUTION_ADMIN".equals(role.role())
                && role.institutionId() != null
                && institutionIds.contains(role.institutionId()));
        if (!platformAdmin && !institutionAdmin) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed"
            );
        }
    }

    public void requireEnrollmentDecisionManager(
        AuthenticatedUserContext context,
        UUID institutionId,
        boolean mainInstructorAssigned
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (institutionId == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", java.util.List.of("institutionId"));
        }
        boolean allowed = context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null)
                || ("INSTITUTION_ADMIN".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
                || ("INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId)
                    && mainInstructorAssigned));
        if (!allowed) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed");
        }
    }

    public void requireEnrollmentSubjectOrDecisionManager(
        AuthenticatedUserContext context,
        UUID subjectUserId,
        UUID institutionId,
        boolean mainInstructorAssigned
    ) {
        if (context == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (Objects.equals(context.userId(), subjectUserId)) {
            return;
        }
        requireEnrollmentDecisionManager(context, institutionId, mainInstructorAssigned);
    }

    private ApiException forbidden() {
        return new ApiException(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.FORBIDDEN,
            "Platform Admin role is required"
        );
    }
}
