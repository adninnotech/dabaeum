package com.adn.dabaeum.blockchain.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageValueTest {

    private static final String HASH = "a".repeat(64);
    private static final Instant EVENT_TIME = Instant.ofEpochMilli(1_723_507_200_123L);

    @Test
    void 모든_상태를_canonical_epoch_millis_문자열로_왕복한다() {
        assertRoundTrip(RegistryStatus.ACTIVE, "A");
        assertRoundTrip(RegistryStatus.REVOKED, "R");
        assertRoundTrip(RegistryStatus.SUPERSEDED, "S");
    }

    @Test
    void 비정규_또는_범위를_벗어난_epoch_millis를_거부한다() {
        for (String epochMillis : List.of(
            "01723507200123", "0", "-1", "+1723507200123", "9223372036854775808"
        )) {
            assertThatThrownBy(() -> StorageValue.decode("1|A|" + HASH + "|" + epochMillis))
                .isInstanceOf(IllegalArgumentException.class);
        }

        assertThatThrownBy(() -> new StorageValue(
            1, RegistryStatus.ACTIVE, HASH, Instant.EPOCH
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageValue(
            1, RegistryStatus.ACTIVE, HASH, Instant.ofEpochMilli(-1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageValue(
            1, RegistryStatus.ACTIVE, HASH, Instant.ofEpochSecond(1, 1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageValue(
            1, RegistryStatus.ACTIVE, HASH, Instant.MAX
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비정규_compact_value를_거부한다() {
        for (String value : List.of(
            "2|A|" + HASH + "|1723507200123",
            "1|X|" + HASH + "|1723507200123",
            "1|A|" + "A".repeat(64) + "|1723507200123",
            "1|A|" + "a".repeat(63) + "|1723507200123",
            "1|A|" + HASH + "|1723507200123|extra",
            "1|A||1723507200123"
        )) {
            assertThatThrownBy(() -> StorageValue.decode(value))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void assertRoundTrip(RegistryStatus status, String statusCode) {
        StorageValue value = new StorageValue(1, status, HASH, EVENT_TIME);
        String encoded = "1|" + statusCode + "|" + HASH + "|1723507200123";

        assertThat(value.encode()).isEqualTo(encoded);
        assertThat(StorageValue.decode(encoded)).isEqualTo(value);
    }
}
