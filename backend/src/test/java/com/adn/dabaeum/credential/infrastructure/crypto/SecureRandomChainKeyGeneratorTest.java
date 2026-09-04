package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRandomChainKeyGeneratorTest {

    @Test
    void 만개의_키를_정확한_형식으로_중복_없이_생성한다() {
        var generator = new SecureRandomChainKeyGenerator(new SequenceSecureRandom());
        Set<String> keys = new LinkedHashSet<>();

        for (int index = 0; index < 10_000; index++) {
            keys.add(generator.generate());
        }

        assertThat(keys)
            .hasSize(10_000)
            .allMatch(key -> key.matches("^[A-Z0-9]{16}$"));
    }

    /** 확률적 충돌 없이 생성기의 alphabet 선택 동작을 검증하는 입력 스트림이다. */
    private static final class SequenceSecureRandom extends SecureRandom {

        private int callCount;

        @Override
        public int nextInt(int bound) {
            if (bound != 36) {
                throw new IllegalArgumentException("지원하지 않는 alphabet 크기");
            }
            int keyIndex = callCount / 16;
            int position = callCount % 16;
            callCount++;
            if (position < 13) {
                return 0;
            }
            int divisor = switch (position) {
                case 13 -> 36 * 36;
                case 14 -> 36;
                default -> 1;
            };
            return (keyIndex / divisor) % 36;
        }
    }
}
