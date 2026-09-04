package com.adn.dabaeum.support.domain;

import java.util.Objects;
import java.util.UUID;

public record CommonCode(
    UUID id,
    String codeGroup,
    String code,
    String name,
    int sortOrder
) {

    public CommonCode {
        Objects.requireNonNull(id, "id");
        if (codeGroup == null || codeGroup.isBlank() || codeGroup.length() > 50) {
            throw new IllegalArgumentException("codeGroup must be 1..50 characters");
        }
        if (code == null || code.isBlank() || code.length() > 50) {
            throw new IllegalArgumentException("code must be 1..50 characters");
        }
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("name must be 1..200 characters");
        }
    }
}
