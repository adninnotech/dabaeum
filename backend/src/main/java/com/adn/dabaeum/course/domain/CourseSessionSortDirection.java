package com.adn.dabaeum.course.domain;

public enum CourseSessionSortDirection {
    ASC("asc"),
    DESC("desc");

    private final String apiValue;

    CourseSessionSortDirection(String apiValue) {
        this.apiValue = apiValue;
    }

    public static CourseSessionSortDirection fromApiValue(String value) {
        for (CourseSessionSortDirection direction : values()) {
            if (direction.apiValue.equals(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException(
            "Unsupported course session sort direction: " + value
        );
    }
}
