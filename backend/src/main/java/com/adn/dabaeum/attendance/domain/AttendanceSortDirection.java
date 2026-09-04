package com.adn.dabaeum.attendance.domain;

public enum AttendanceSortDirection {
    ASC("asc"),
    DESC("desc");

    private final String apiValue;

    AttendanceSortDirection(String apiValue) {
        this.apiValue = apiValue;
    }

    public static AttendanceSortDirection fromApiValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }
        for (AttendanceSortDirection direction : values()) {
            if (direction.apiValue.equals(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException(
            "Unsupported attendance sort direction: " + value
        );
    }

    public String apiValue() {
        return apiValue;
    }
}
