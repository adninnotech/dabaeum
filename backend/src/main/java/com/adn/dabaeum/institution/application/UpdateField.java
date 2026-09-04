package com.adn.dabaeum.institution.application;

public record UpdateField<T>(boolean present, T value) {

    public static <T> UpdateField<T> absent() {
        return new UpdateField<>(false, null);
    }

    public static <T> UpdateField<T> present(T value) {
        return new UpdateField<>(true, value);
    }
}
