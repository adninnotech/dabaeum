package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

/**
 * 어댑터의 요청 형식과 오류 분류를 실 OpenBao 로 확인한다. 특히 min_encryption_version 거부 문구에
 * "minimum" 이 들어 있는지가 VERSION_REJECTED 분류의 근거다. 키 이름을 고정해 반복 실행해도 늘지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "BAO_ADDR", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BAO_TOKEN", matches = ".+")
class OpenBaoTransitSigningAdapterSpikeTest {

    private static final String MOUNT = "education-sign";
    private static final String KEY = "spike-ed25519";
    private static final String MIN_VERSION_KEY = "spike-min-version-ed25519";

    private final VaultTemplate vault = new VaultTemplate(
        VaultEndpoint.from(URI.create(System.getenv("BAO_ADDR"))),
        new TokenAuthentication(System.getenv("BAO_TOKEN")));
    private final OpenBaoTransitSigningAdapter adapter = new OpenBaoTransitSigningAdapter(vault);
    private final byte[] input =
        "eyJhbGciOiJFZERTQSJ9.eyJoZWxsbyI6Im9wZW5iYW8ifQ".getBytes(StandardCharsets.US_ASCII);

    @Test
    void 명시한_버전으로_서명하고_공개키로_검증된다() throws Exception {
        vault.write(MOUNT + "/keys/" + KEY, Map.of("type", "ed25519"));

        TransitSignature signature = adapter.sign(MOUNT, KEY, 1, input);

        assertThat(signature.keyVersion()).isEqualTo(1);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey(KEY, 1));
        verifier.update(input);
        assertThat(verifier.verify(signature.signature())).isTrue();
    }

    @Test
    void 최소_서명_버전보다_낮은_버전은_VERSION_REJECTED_다() {
        vault.write(MOUNT + "/keys/" + MIN_VERSION_KEY, Map.of("type", "ed25519"));
        Number latest = (Number) vault.read(MOUNT + "/keys/" + MIN_VERSION_KEY).getData().get("latest_version");
        if (latest.intValue() < 2) {
            vault.write(MOUNT + "/keys/" + MIN_VERSION_KEY + "/rotate");
        }
        vault.write(MOUNT + "/keys/" + MIN_VERSION_KEY + "/config", Map.of("min_encryption_version", 2));

        assertThatThrownBy(() -> adapter.sign(MOUNT, MIN_VERSION_KEY, 1, input))
            .isInstanceOfSatisfying(TransitSigningException.class, exception -> {
                assertThat(exception.reason()).isEqualTo(TransitSigningException.Reason.VERSION_REJECTED);
                assertThat(exception.getCause().getMessage()).containsIgnoringCase("minimum");
            });
    }

    @Test
    void 없는_키는_REJECTED_다() {
        assertThatThrownBy(() -> adapter.sign(MOUNT, "spike-missing-ed25519", 1, input))
            .isInstanceOfSatisfying(TransitSigningException.class,
                exception -> assertThat(exception.reason()).isEqualTo(TransitSigningException.Reason.REJECTED));
    }

    @SuppressWarnings("unchecked")
    private PublicKey publicKey(String key, int version) throws Exception {
        Map<String, Object> keys = (Map<String, Object>) vault.read(MOUNT + "/keys/" + key).getData().get("keys");
        String encoded = (String) ((Map<String, Object>) keys.get(String.valueOf(version))).get("public_key");
        byte[] raw = Base64.getDecoder().decode(encoded);
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        byte[] spki = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, spki, 0, prefix.length);
        System.arraycopy(raw, 0, spki, prefix.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
    }
}
