package com.adn.dabaeum.user.application;

import java.util.function.Function;

public record UserUpdateField<T>(boolean present, T value) {

    public static <T> UserUpdateField<T> absent() {
        return new UserUpdateField<>(false, null);
    }

    public static <T> UserUpdateField<T> present(T value) {
        return new UserUpdateField<>(true, value);
    }

    /** 보낸 값에만 변환을 적용한다. 안 보낸 필드는 그대로 absent. */
    public <R> UserUpdateField<R> map(Function<? super T, ? extends R> mapper) {
        return present ? present(mapper.apply(value)) : absent();
    }

    /** 보냈으면 그 값, 안 보냈으면 기존 값. */
    public T orElse(T existing) {
        return present ? value : existing;
    }
}
