package com.adn.dabaeum.institution.domain;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }

        return switch (value) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new IllegalArgumentException(
                "Unsupported sort direction: " + value
            );
        };
    }
}
