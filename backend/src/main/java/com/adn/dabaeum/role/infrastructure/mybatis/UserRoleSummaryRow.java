package com.adn.dabaeum.role.infrastructure.mybatis;

import java.util.UUID;

/** 역할 + 기관명 조인 결과 한 행. 기관이 없는 전역 역할은 기관 컬럼이 모두 null 이다. */
public record UserRoleSummaryRow(
    UUID userId,
    String role,
    UUID institutionId,
    String institutionName
) {
}
