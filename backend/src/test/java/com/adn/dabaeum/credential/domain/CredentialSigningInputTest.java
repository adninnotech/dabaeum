package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 설계서 §7.2·§7.4: signing input 은 헤더와 payload 두 조각을 점으로 이은 ASCII 바이트다. */
class CredentialSigningInputTest {

    private static final String HEADER = "aGVhZGVy";
    private static final String PAYLOAD = "cGF5bG9hZA";

    @Test
    void 서명_입력은_두_조각을_점으로_이은_ASCII_바이트다() {
        CredentialSigningInput input = new CredentialSigningInput(HEADER, PAYLOAD);

        assertThat(new String(input.bytes(), StandardCharsets.US_ASCII)).isEqualTo("aGVhZGVy.cGF5bG9hZA");
        assertThat(input.document().canonicalJson()).isEqualTo("payload");
    }

    @Test
    void 봉투는_서명을_패딩_없는_base64url_로_붙인_compact_JWS_다() {
        byte[] signature = new byte[64];
        signature[0] = (byte) 0xfb;
        signature[63] = (byte) 0xff;

        SignedCredentialEnvelope envelope = new CredentialSigningInput(HEADER, PAYLOAD).envelope(signature);

        assertThat(envelope.mediaType()).isEqualTo("application/vc+jwt");
        String[] segments = envelope.compactJws().split("\\.", -1);
        assertThat(segments).hasSize(3);
        assertThat(segments[0]).isEqualTo(HEADER);
        assertThat(segments[1]).isEqualTo(PAYLOAD);
        assertThat(segments[2]).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(Base64.getUrlDecoder().decode(segments[2])).isEqualTo(signature);
    }

    @Test
    void 패딩이나_표준_base64_문자와_64바이트가_아닌_서명은_거부한다() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CredentialSigningInput("aGVhZGVy=", PAYLOAD));
        assertThatIllegalArgumentException().isThrownBy(() -> new CredentialSigningInput("a+b", PAYLOAD));
        assertThatIllegalArgumentException().isThrownBy(() -> new CredentialSigningInput(HEADER, ""));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new CredentialSigningInput(HEADER, PAYLOAD).envelope(new byte[63]));
    }
}
