package com.adn.dabaeum.role.application;

import com.adn.dabaeum.role.domain.UserRole;
import java.util.UUID;

/** 로그인한 사용자가 INSTRUCTOR·INSTITUTION_ADMIN 으로 소속된 기관 하나. */
public record MyInstitution(
    UUID institutionId,
    String name,
    UserRole role
) {
}
