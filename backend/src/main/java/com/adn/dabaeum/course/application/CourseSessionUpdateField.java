package com.adn.dabaeum.course.application;

public record CourseSessionUpdateField<T>(boolean present, T value) {

    public static <T> CourseSessionUpdateField<T> absent() {
        return new CourseSessionUpdateField<>(false, null);
    }

    public static <T> CourseSessionUpdateField<T> present(T value) {
        return new CourseSessionUpdateField<>(true, value);
    }
}
