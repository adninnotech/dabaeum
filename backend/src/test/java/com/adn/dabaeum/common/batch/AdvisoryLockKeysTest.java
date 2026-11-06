package com.adn.dabaeum.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AdvisoryLockKeysTest {

    @Test
    void everyKeyIsAUniqueLongConstant() throws IllegalAccessException {
        long[] keys = Arrays.stream(AdvisoryLockKeys.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == long.class)
            .mapToLong(AdvisoryLockKeysTest::read)
            .toArray();

        assertThat(keys).isNotEmpty();
        assertThat(Arrays.stream(keys).distinct().count()).isEqualTo(keys.length);
    }

    private static long read(Field field) {
        try {
            return field.getLong(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
