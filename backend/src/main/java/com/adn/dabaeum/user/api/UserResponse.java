package com.adn.dabaeum.user.api;

import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String name,
    String email,
    String phone,
    LocalDate birthDate,
    UserStatus status,
    Instant withdrawnAt,
    Instant createdAt,
    Instant updatedAt,
    String career,
    String introduction,
    UUID profileImageId,
    Gender gender,
    Instant lastLoginAt,
    boolean emailNotificationEnabled,
    boolean smsNotificationEnabled,
    /**
     * 관리자 전용. 본인 조회(/users/me)에는 나오지 않는다 —
     * UserApiMapper 가 includeAdminMemo=false 일 때 null 을 넣고, 이 애노테이션이
     * null 을 JSON 에서 아예 지운다(프로젝트에 전역 NON_NULL 설정이 없어 여기서만 켠다).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String adminMemo,
    /** 계약상 필수. 역할이 없으면 빈 배열이며 절대 null 이 아니다. */
    List<RoleSummary> roles,
    /**
     * 관리자 회원 목록(GET /users) 전용. 그 외 조회에는 나오지 않는다 — adminMemo 와 같은 방식이다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long enrollmentCount,
    /**
     * 관리자 회원 목록(GET /users) 전용. 그 외 조회에는 나오지 않는다 — adminMemo 와 같은 방식이다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long courseCount,
    /** 본인 조회·수정 전용. 그 외 응답에는 나오지 않는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> interestCodes,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean interestAgreed
) {

    UserResponse withInterests(List<String> codes, boolean agreed) {
        return new UserResponse(id, name, email, phone, birthDate, status, withdrawnAt, createdAt, updatedAt,
            career, introduction, profileImageId, gender, lastLoginAt,
            emailNotificationEnabled, smsNotificationEnabled, adminMemo, roles, enrollmentCount, courseCount,
            List.copyOf(codes), agreed);
    }
}
