package com.adn.dabaeum.user.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 회원 목록(selectPage) 한 행. UserRow 와 컬럼이 같고, 목록 전용 스칼라 서브쿼리 두 개
 * (enrollment_count, course_count)만 더 얹는다. selectById 는 이 서브쿼리를 계산하지 않는다.
 */
public record UserListRow(
    UUID id,
    String name,
    String email,
    String phone,
    LocalDate birthDate,
    String status,
    Instant withdrawnAt,
    Instant createdAt,
    Instant updatedAt,
    String career,
    String introduction,
    UUID profileImageId,
    String gender,
    Instant lastLoginAt,
    Boolean emailNotificationEnabled,
    Boolean smsNotificationEnabled,
    String adminMemo,
    Long enrollmentCount,
    Long courseCount
) {
}
