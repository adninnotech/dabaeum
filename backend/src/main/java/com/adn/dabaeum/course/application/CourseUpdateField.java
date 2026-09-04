package com.adn.dabaeum.course.application;

public record CourseUpdateField<T>(boolean present, T value) {

    public static <T> CourseUpdateField<T> absent() {
        return new CourseUpdateField<>(false, null);
    }

    public static <T> CourseUpdateField<T> present(T value) {
        return new CourseUpdateField<>(true, value);
    }
}
