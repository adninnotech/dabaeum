package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/** 정수 칸의 소수는 버리지 않고 거부한다 (QA VAL-06). */
class JacksonConfigurationTest {

    record Sample(Integer boxed, int primitive, Long wide) {
    }

    private final JsonMapper mapper = strictMapper();

    @Test
    void rejectsFractionInIntegerFields() {
        for (String json : new String[] {
            "{\"boxed\":1.5,\"primitive\":1,\"wide\":1}",
            "{\"boxed\":1,\"primitive\":1.5,\"wide\":1}",
            "{\"boxed\":1,\"primitive\":1,\"wide\":1.5}"
        }) {
            assertThatThrownBy(() -> mapper.readValue(json, Sample.class))
                .isInstanceOf(MismatchedInputException.class);
        }
    }

    @Test
    void keepsAcceptingWholeNumbers() {
        assertThat(mapper.readValue("{\"boxed\":30,\"primitive\":-1,\"wide\":2147483648}", Sample.class))
            .isEqualTo(new Sample(30, -1, 2147483648L));
    }

    private static JsonMapper strictMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfiguration().strictJsonProperties().customize(builder);
        return builder.build();
    }
}
