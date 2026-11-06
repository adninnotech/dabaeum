package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

    @Test
    void generatesFourteenCharactersFromTheUnambiguousAlphabet() {
        String password = TemporaryPasswords.generate(new SecureRandom());

        assertThat(password).hasSize(14).matches("[A-HJ-NP-Za-km-z2-9!@#$]{14}");
    }
}
