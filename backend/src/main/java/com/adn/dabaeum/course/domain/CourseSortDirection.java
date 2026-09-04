package com.adn.dabaeum.course.domain;

public enum CourseSortDirection {
    ASC,
    DESC;

    public static CourseSortDirection fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }
        return switch (value) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new IllegalArgumentException(
                "Unsupported course sort direction: " + value
            );
        };
    }
}
