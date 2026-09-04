package com.adn.dabaeum.user.application;

public record UserUpdateField<T>(boolean present, T value) {

    public static <T> UserUpdateField<T> absent() {
        return new UserUpdateField<>(false, null);
    }

    public static <T> UserUpdateField<T> present(T value) {
        return new UserUpdateField<>(true, value);
    }
}
