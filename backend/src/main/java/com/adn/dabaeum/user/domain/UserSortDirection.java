package com.adn.dabaeum.user.domain;

public enum UserSortDirection {
    ASC,
    DESC;

    public static UserSortDirection fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }
        return switch (value) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new IllegalArgumentException(
                "Unsupported user sort direction: " + value
            );
        };
    }
}
