package com.adn.dabaeum.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class UserSortTest {

    @ParameterizedTest
    @CsvSource({
        "createdAt,CREATED_AT",
        "updatedAt,UPDATED_AT",
        "name,NAME",
        "email,EMAIL",
        "status,STATUS"
    })
    void acceptsOnlyWhitelistedSort(String apiValue, UserSort expected) {
        assertThat(UserSort.fromApiValue(apiValue)).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> UserSort.fromApiValue("password"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
