package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.CredentialHashVersion;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CredentialHashServiceTest {

    private final CredentialHashService hashService = new CredentialHashService(new ObjectMapper());

    @Test
    void serializesExactEnvelopeBytesAndComputesLowercaseSha256() {
        var envelope = new SignedCredentialEnvelope(
            "application/vc+jwt", "header.payload.signature");

        assertThat(hashService.serialize(envelope)).isEqualTo(
            "{\"mediaType\":\"application/vc+jwt\",\"compactJws\":\"header.payload.signature\"}");
        assertThat(hashService.sha256(envelope)).isEqualTo(
            "1ef7ad25db13b66481874bca8d19038f58e95e59448439763df53caf5d83f23a");
        assertThat(hashService.sha256(envelope)).matches("[0-9a-f]{64}");
    }

    @Test
    void hashesTheRawPersistedPayloadWithoutCanonicalizingWhitespace() {
        String raw = "{\"mediaType\": \"application/vc+jwt\", "
            + "\"compactJws\":\"header.payload.signature\"}";

        assertThat(hashService.sha256Payload(raw))
            .isEqualTo("c81cf2bec4dd6ec906bd6e843ad0b509a5b1e9e651800639a7af3ab292b181ef");
        assertThat(hashService.sha256Payload(raw))
            .isNotEqualTo(hashService.sha256(new SignedCredentialEnvelope(
                "application/vc+jwt", "header.payload.signature")));
    }

    @Test
    void 해시_버전은_레거시_envelope과_신규_compact_jws만_지원한다() {
        assertThat(CredentialHashVersion.values()).containsExactly(
            CredentialHashVersion.ENVELOPE_SHA256_V0,
            CredentialHashVersion.COMPACT_JWS_SHA256_V1
        );
    }

    @Test
    void compact_jws_전체_utf8_바이트를_그대로_해시한다() {
        assertThat(hashService.sha256CompactJws("header.payload.signature"))
            .isEqualTo("256d04db4e5e4ac308751ed0885b722b758630567c53a7125ed9fbd068e5c3f6")
            .matches("[0-9a-f]{64}");
        assertThat(hashService.sha256CompactJws("header.payload.signature"))
            .isEqualTo(hashService.sha256CompactJws("header.payload.signature"));
    }

    @Test
    void compact_jws의_각_segment가_한_글자라도_바뀌면_해시가_달라진다() {
        String original = hashService.sha256CompactJws("header.payload.signature");

        assertThat(hashService.sha256CompactJws("Header.payload.signature")).isNotEqualTo(original);
        assertThat(hashService.sha256CompactJws("header.Payload.signature")).isNotEqualTo(original);
        assertThat(hashService.sha256CompactJws("header.payload.Signature")).isNotEqualTo(original);
    }

    @Test
    void 신규_compact_jws_해시는_기존_envelope_해시와_구분된다() {
        var envelope = new SignedCredentialEnvelope(
            "application/vc+jwt", "header.payload.signature");

        assertThat(hashService.sha256CompactJws(envelope.compactJws()))
            .isNotEqualTo(hashService.sha256(envelope));
        assertThat(hashService.sha256(envelope))
            .isEqualTo("1ef7ad25db13b66481874bca8d19038f58e95e59448439763df53caf5d83f23a");
    }
}
