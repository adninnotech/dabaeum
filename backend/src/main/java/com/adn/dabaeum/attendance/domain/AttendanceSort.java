package com.adn.dabaeum.attendance.domain;

public enum AttendanceSort {
    CHECKED_AT("checkedAt"),
    CREATED_AT("createdAt"),
    STATUS("status");

    private final String apiValue;

    AttendanceSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public static AttendanceSort fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("sort is required");
        }
        for (AttendanceSort sort : values()) {
            if (sort.apiValue.equals(value)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Unsupported attendance sort: " + value);
    }

    public String apiValue() {
        return apiValue;
    }
}
