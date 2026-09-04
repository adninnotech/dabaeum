package com.adn.dabaeum.course.domain;

public enum CourseSessionSort {
    SESSION_NO("sessionNo"),
    STARTS_AT("startsAt"),
    ENDS_AT("endsAt"),
    STATUS("status"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String apiValue;

    CourseSessionSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public static CourseSessionSort fromApiValue(String value) {
        for (CourseSessionSort sort : values()) {
            if (sort.apiValue.equals(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unsupported course session sort: " + value);
    }
}
