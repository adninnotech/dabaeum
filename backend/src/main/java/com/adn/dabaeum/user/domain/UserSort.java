package com.adn.dabaeum.user.domain;

public enum UserSort {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    NAME("name"),
    EMAIL("email"),
    STATUS("status");

    private final String apiValue;

    UserSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public static UserSort fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("sort is required");
        }
        return switch (value) {
            case "createdAt" -> CREATED_AT;
            case "updatedAt" -> UPDATED_AT;
            case "name" -> NAME;
            case "email" -> EMAIL;
            case "status" -> STATUS;
            default -> throw new IllegalArgumentException(
                "Unsupported user sort: " + value
            );
        };
    }

    public String apiValue() {
        return apiValue;
    }
}
