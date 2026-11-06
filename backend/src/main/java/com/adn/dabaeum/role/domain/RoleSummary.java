package com.adn.dabaeum.role.domain;

import java.util.UUID;

/**
 * 사용자 응답에 붙는 역할 요약. 기관명까지 한 번에 내려 주어야 회원 목록 화면이
 * 행마다 기관을 다시 조회하지 않는다. 전역 역할(PLATFORM_ADMIN)은 기관이 없어
 * {@code institutionId}·{@code institutionName} 이 모두 {@code null} 이다.
 */
public record RoleSummary(
    UserRole role,
    UUID institutionId,
    String institutionName
) {
}
