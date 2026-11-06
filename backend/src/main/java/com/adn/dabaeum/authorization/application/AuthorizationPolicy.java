package com.adn.dabaeum.authorization.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 초기 Stage 3 역할 관리 인가 규칙을 한곳에서 관리한다.
 */
@Component
public final class AuthorizationPolicy {

    private static final String ROLE_INSTITUTION_ADMIN = "INSTITUTION_ADMIN";
    private static final String ROLE_INSTRUCTOR = "INSTRUCTOR";
    private static final Set<String> INSTITUTION_STAFF_ROLES =
        Set.of(ROLE_INSTITUTION_ADMIN, ROLE_INSTRUCTOR);

    public void requirePlatformAdmin(AuthenticatedUserContext context) {
        if (!isPlatformAdmin(context)) {
            throw forbidden();
        }
    }

    public void requireInstitutionAdmin(AuthenticatedUserContext context, UUID institutionId) {
        if (context == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (context.roles().stream().noneMatch(role ->
            ROLE_INSTITUTION_ADMIN.equals(role.role())
                && Objects.equals(role.institutionId(), institutionId))) {
            throw forbidden();
        }
    }

    /**
     * 같은 규칙을 던지지 않고 물어보는 형태. {@link #requirePlatformAdmin(AuthenticatedUserContext)}
     * 가 이것으로 판정한다.
     */
    public boolean isPlatformAdmin(AuthenticatedUserContext context) {
        return context != null && context.roles().stream().anyMatch(role ->
            "PLATFORM_ADMIN".equals(role.role())
                && role.institutionId() == null
        );
    }

    /**
     * 내가 직원(INSTITUTION_ADMIN 또는 INSTRUCTOR)인 기관 id 목록. {@link #isInstitutionStaff}
     * 와 같은 역할 집합({@link #INSTITUTION_STAFF_ROLES})을 쓴다. context 가 null 이면 빈 목록.
     */
    public List<UUID> institutionStaffIds(AuthenticatedUserContext context) {
        if (context == null) {
            return List.of();
        }
        return context.roles().stream()
            .filter(role -> role.institutionId() != null
                && INSTITUTION_STAFF_ROLES.contains(role.role()))
            .map(AuthenticatedRole::institutionId)
            .distinct()
            .toList();
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
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                java.util.List.of("institutionId")
            );
        }

        if (!isCourseManager(context, institutionId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed"
            );
        }
    }

    /**
     * 같은 규칙을 던지지 않고 물어보는 형태. 인증만 하면 누구나 여는 화면에서 "관리자에게만
     * 보이는 필드" 를 정할 때 쓴다 — 역할 이름만 보면 남의 기관 것까지 열린다.
     */
    public boolean isCourseManager(AuthenticatedUserContext context, UUID institutionId) {
        if (context == null || institutionId == null) {
            return false;
        }
        return context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role())
                && role.institutionId() == null)
                || ("INSTITUTION_ADMIN".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
        );
    }

    /**
     * "그 기관 소속인가" — {@link #isCourseManager} 에 INSTRUCTOR 까지 더한 것이다. 강사를
     * 관리자로 쳐도 되는 화면(예: 그 기관 강좌의 DRAFT 가시성)에서 쓴다. 특정 강좌에 주강사로
     * 배정됐는지는 보지 않는다 — 그걸 봐야 하면 {@link #isCourseSessionManager} 를 쓴다.
     */
    public boolean isInstitutionStaff(AuthenticatedUserContext context, UUID institutionId) {
        return isCourseSessionManager(context, institutionId, true);
    }

    /**
     * 강좌 생성 시점에는 아직 강좌(및 MAIN 강사 배정)가 없어 {@link #requireCourseSessionManager}
     * 를 쓸 수 없다. PLATFORM_ADMIN(전역) / 기관 범위 INSTITUTION_ADMIN / 기관 범위 INSTRUCTOR 를
     * 허용한다.
     */
    public void requireCourseCreator(
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
                HttpStatus.UNPROCESSABLE_CONTENT,
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
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                java.util.List.of("institutionId")
            );
        }

        if (!isCourseSessionManager(context, institutionId, mainInstructorAssigned)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed"
            );
        }
    }

    /**
     * 같은 규칙을 던지는 대신 물어보는 형태. 관리자와 학습자가 같은 엔드포인트를 쓰는 화면에서
     * "관리자에게만 보이는 필드" 를 정할 때 쓴다 — 역할 이름만 보면 남의 기관 건까지 열린다.
     */
    public boolean isCourseSessionManager(
        AuthenticatedUserContext context,
        UUID institutionId,
        boolean mainInstructorAssigned
    ) {
        if (context == null || institutionId == null) {
            return false;
        }
        return context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null)
                || (ROLE_INSTITUTION_ADMIN.equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
                || (ROLE_INSTRUCTOR.equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId)
                    && mainInstructorAssigned)
        );
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
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
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
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", java.util.List.of("institutionId"));
        }
        if (!isEnrollmentDecisionManager(context, institutionId, mainInstructorAssigned)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Institution scope is not allowed");
        }
    }

    /** 수강 결정 권한자인가: 플랫폼 관리자, 그 기관 관리자, 그 과정의 MAIN 강사. */
    public boolean isEnrollmentDecisionManager(
        AuthenticatedUserContext context,
        UUID institutionId,
        boolean mainInstructorAssigned
    ) {
        return context != null && institutionId != null && context.roles().stream().anyMatch(role ->
            ("PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null)
                || ("INSTITUTION_ADMIN".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId))
                || ("INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), institutionId)
                    && mainInstructorAssigned));
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
