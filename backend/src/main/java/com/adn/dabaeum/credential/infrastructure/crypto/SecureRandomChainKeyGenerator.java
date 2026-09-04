package com.adn.dabaeum.credential.infrastructure.crypto;

import com.adn.dabaeum.credential.application.port.ChainKeyGenerator;
import java.security.SecureRandom;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** SecureRandom으로 개인정보를 포함하지 않는 원장 키를 생성한다. */
@Component
public class SecureRandomChainKeyGenerator implements ChainKeyGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int KEY_LENGTH = 16;

    private final SecureRandom secureRandom;

    public SecureRandomChainKeyGenerator() {
        this(new SecureRandom());
    }

    SecureRandomChainKeyGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public String generate() {
        StringBuilder key = new StringBuilder(KEY_LENGTH);
        for (int index = 0; index < KEY_LENGTH; index++) {
            key.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return key.toString();
    }
}
