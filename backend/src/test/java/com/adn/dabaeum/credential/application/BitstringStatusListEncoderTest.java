package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class BitstringStatusListEncoderTest {

    private static final int CAPACITY = 131_072;

    private final BitstringStatusListEncoder encoder = new BitstringStatusListEncoder();

    @Test
    void emptyListCompressesToMultibaseGzipAndDecodesToAllZeroBits() {
        String encoded = encoder.encode(CAPACITY, List.of());

        assertThat(encoded).startsWith("uH4sI");
        assertThat(encoded).doesNotContain("=");
        assertThat(encoded.length()).isLessThan(120);
        assertThat(encoder.decode(encoded, CAPACITY).cardinality()).isZero();
    }

    @Test
    void setsExactlyTheRevokedIndexesWithBitZeroAsTheMostSignificantBitOfTheFirstByte() {
        String encoded = encoder.encode(CAPACITY, List.of(0, 7, 8, 94_567, CAPACITY - 1));

        BitSet bits = encoder.decode(encoded, CAPACITY);
        assertThat(bits.stream().boxed().toList()).containsExactly(0, 7, 8, 94_567, CAPACITY - 1);

        // 비트 0 은 첫 바이트의 0x80 자리에 있어야 한다.
        String first = encoder.encode(8, List.of(0));
        assertThat(encoder.decode(first, 8).get(0)).isTrue();
        assertThat(encoder.decode(first, 8).get(7)).isFalse();
    }

    @Test
    void sameContentEncodesIdentically() {
        assertThat(encoder.encode(CAPACITY, List.of(3, 5)))
            .isEqualTo(encoder.encode(CAPACITY, List.of(5, 3)));
    }

    @Test
    void rejectsIndexesOutsideTheListAndMalformedEncodings() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> encoder.encode(CAPACITY, List.of(CAPACITY)));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> encoder.encode(CAPACITY, List.of(-1)));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> encoder.encode(12, List.of()));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> encoder.decode("zH4sI", CAPACITY));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> encoder.decode("uAAAA", CAPACITY));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> encoder.decode(encoder.encode(8, List.of()), CAPACITY));
    }
}
