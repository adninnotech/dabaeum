package com.adn.dabaeum.course.application;

import java.util.function.Function;

public record CourseUpdateField<T>(boolean present, T value) {

    public static <T> CourseUpdateField<T> absent() {
        return new CourseUpdateField<>(false, null);
    }

    public static <T> CourseUpdateField<T> present(T value) {
        return new CourseUpdateField<>(true, value);
    }

    /** 보낸 값에만 변환을 적용한다. 안 보낸 필드는 그대로 absent. */
    public <R> CourseUpdateField<R> map(Function<? super T, ? extends R> mapper) {
        return present ? present(mapper.apply(value)) : absent();
    }
}
