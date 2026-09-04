package com.adn.dabaeum.enrollment.domain;

public enum EnrollmentSort {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    APPLIED_AT("appliedAt"),
    STATUS("status"),
    USER_ID("userId");

    private final String apiValue;

    EnrollmentSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public static EnrollmentSort fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("sort is required");
        }
        return switch (value) {
            case "createdAt" -> CREATED_AT;
            case "updatedAt" -> UPDATED_AT;
            case "appliedAt" -> APPLIED_AT;
            case "status" -> STATUS;
            case "userId" -> USER_ID;
            default -> throw new IllegalArgumentException("Unsupported enrollment sort: " + value);
        };
    }

    public String apiValue() {
        return apiValue;
    }
}
