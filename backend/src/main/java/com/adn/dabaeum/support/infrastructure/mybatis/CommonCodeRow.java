package com.adn.dabaeum.support.infrastructure.mybatis;

import java.util.UUID;

public record CommonCodeRow(
    UUID id,
    String codeGroup,
    String code,
    String name,
    Integer sortOrder
) {
}
