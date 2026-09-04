package com.adn.dabaeum.enrollment.domain;

public enum EnrollmentSortDirection {
    ASC,
    DESC;

    public static EnrollmentSortDirection fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }
        return switch (value) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new IllegalArgumentException(
                "Unsupported enrollment sort direction: " + value);
        };
    }
}
