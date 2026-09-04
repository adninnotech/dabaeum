package com.adn.dabaeum.institution.domain;

public enum InstitutionSort {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    INSTITUTION_CODE("institutionCode"),
    NAME("name"),
    STATUS("status");

    private final String apiValue;

    InstitutionSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public static InstitutionSort fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("sort is required");
        }

        return switch (value) {
            case "createdAt" -> CREATED_AT;
            case "updatedAt" -> UPDATED_AT;
            case "institutionCode" -> INSTITUTION_CODE;
            case "name" -> NAME;
            case "status" -> STATUS;
            default -> throw new IllegalArgumentException(
                "Unsupported institution sort: " + value
            );
        };
    }

    public String apiValue() {
        return apiValue;
    }
}
