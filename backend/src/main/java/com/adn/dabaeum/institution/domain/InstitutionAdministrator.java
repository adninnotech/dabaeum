package com.adn.dabaeum.institution.domain;

import java.util.UUID;

/** 기관 상세가 보여주는 관리자 한 명. tb_user_roles 의 INSTITUTION_ADMIN 을 역방향으로 조회한 결과다. */
public record InstitutionAdministrator(
    UUID id,
    String name,
    String email,
    String phone
) {
}
