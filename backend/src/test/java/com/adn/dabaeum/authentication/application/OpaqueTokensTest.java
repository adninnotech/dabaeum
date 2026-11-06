package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpaqueTokensTest {

    @Test
    void tokensAreUrlSafe256BitAndUnique() {
        String first = OpaqueTokens.newToken();
        String second = OpaqueTokens.newToken();

        assertThat(first).matches("^[A-Za-z0-9_-]{43}$"); // 32바이트, 패딩 없음
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashIsLowercaseSha256Hex() {
        assertThat(OpaqueTokens.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
