package com.adn.dabaeum.role.infrastructure.mybatis;

import java.util.UUID;

/** tb_user_roles 의 INSTITUTION_ADMIN 을 tb_users 와 조인한 결과 한 행. */
public record InstitutionAdministratorRow(
    UUID id,
    String name,
    String email,
    String phone
) {
}
