package com.adn.dabaeum.credential.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class KmsRecoveryPropertiesTest {

    @Test
    void 지연은_양수_배치는_1_에서_100_사이여야_한다() {
        assertThatCode(() -> new KmsRecoveryProperties(true, Duration.ofSeconds(10), Duration.ofMinutes(5), 20))
            .doesNotThrowAnyException();
        assertThatCode(() -> new KmsRecoveryProperties(false, Duration.ZERO, Duration.ofMinutes(5), 1))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> new KmsRecoveryProperties(true, null, Duration.ofMinutes(5), 20))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KmsRecoveryProperties(true, Duration.ofSeconds(10), Duration.ZERO, 20))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KmsRecoveryProperties(true, Duration.ofSeconds(10), Duration.ofMinutes(5), 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KmsRecoveryProperties(true, Duration.ofSeconds(10), Duration.ofMinutes(5), 101))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
