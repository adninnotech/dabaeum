package com.adn.dabaeum.course.domain;

public enum CourseSort {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    COURSE_CODE("courseCode"),
    TITLE("title"),
    STATUS("status"),
    START_DATE("startDate"),
    END_DATE("endDate");

    private final String apiValue;

    CourseSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public static CourseSort fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("sort is required");
        }

        return switch (value) {
            case "createdAt" -> CREATED_AT;
            case "updatedAt" -> UPDATED_AT;
            case "courseCode" -> COURSE_CODE;
            case "title" -> TITLE;
            case "status" -> STATUS;
            case "startDate" -> START_DATE;
            case "endDate" -> END_DATE;
            default -> throw new IllegalArgumentException(
                "Unsupported course sort: " + value
            );
        };
    }

    public String apiValue() {
        return apiValue;
    }
}
