package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

/**
 * 설계서 §7.2·§7.3 의 전제를 실 OpenBao 로 확인한다. ed25519 키는 prehashed 없이 JWS signing
 * input 원문에 서명하고, 같은 입력·버전이면 같은 서명이 나오며, 응답 접두어 vault:vN: 뒤의
 * base64 를 base64url 로 바꾸면 Ed25519 공개키로 검증된다.
 *
 * <p>prehashed 요청은 계획 1 Task 2 실측(reports/plan-1-task-2.md)에서 OpenBao 2.6.2 가
 * 수용하는 것으로 확인됐고, 이 스파이크에서 그 서명이 원문 서명과 바이트 단위로 같다는 것까지
 * 확인했다. 즉 ed25519 에서는 파라미터가 무시된다(Ed25519ph 가 아니다). VC 서명은 원문 서명만
 * 쓰고 이 파라미터를 보내지 않는다 (설계서 §21 "서명 입력").
 *
 * <p>BAO_ADDR 와 BAO_TOKEN(키 생성 권한) 이 있을 때만 돈다. 키 이름을 고정해 실행할 때마다
 * 키가 늘어나지 않게 한다 (Transit 은 같은 이름 생성 요청을 무시한다).
 */
@EnabledIfEnvironmentVariable(named = "BAO_ADDR", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BAO_TOKEN", matches = ".+")
class OpenBaoTransitSpikeTest {

    private static final String MOUNT = "education-sign";
    private static final String KEY = "spike-ed25519";
    private static final String PREFIX = "vault:v";

    private final VaultTemplate vault = new VaultTemplate(
        VaultEndpoint.from(URI.create(System.getenv("BAO_ADDR"))),
        new TokenAuthentication(System.getenv("BAO_TOKEN")));

    private final byte[] signingInput =
        (base64Url("{\"alg\":\"EdDSA\",\"typ\":\"vc+jwt\"}") + "." + base64Url("{\"hello\":\"openbao\"}"))
            .getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void createKeyIfMissing() {
        // 파라미터를 명시하지 않는다. exportable 등은 기본값 false 이고 관리자 Policy 가 명시를 거부한다 (§5.3).
        vault.write(MOUNT + "/keys/" + KEY, Map.of("type", "ed25519"));
    }

    @Test
    void ed25519_는_prehashed_없이_key_version_을_지정해_서명하고_공개키로_검증된다() throws Exception {
        String signature = sign(1);

        assertThat(signature).startsWith(PREFIX + "1:");
        byte[] raw = Base64.getDecoder().decode(signature.substring((PREFIX + "1:").length()));
        String jwsSegment = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey(1));
        verifier.update(signingInput);
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwsSegment))).isTrue();
    }

    @Test
    void 같은_입력과_버전을_두_번_서명하면_바이트가_같다() {
        assertThat(sign(1)).isEqualTo(sign(1));
    }

    @Test
    void prehashed_파라미터는_수용되지만_무시되어_원문_서명과_같은_서명이_나온다() {
        // 실측: OpenBao 2.6.2 는 ed25519 에 prehashed=true 를 거부하지 않고, 서명은 원문 서명과 같다.
        // Ed25519ph 의미론이 없다는 뜻이다. 입력으로 다이제스트를 넘기면 다이제스트 바이트에 서명해
        // JWS 검증이 깨지므로, 서명 서비스는 이 파라미터를 보내지 않고 항상 signing input 원문을 넘긴다.
        Map<String, Object> data = vault.write(MOUNT + "/sign/" + KEY, Map.of(
            "input", Base64.getEncoder().encodeToString(signingInput),
            "key_version", 1,
            "prehashed", true,
            "hash_algorithm", "sha2-256")).getData();
        String prehashedSignature = (String) data.get("signature");

        assertThat(prehashedSignature).startsWith(PREFIX + "1:");
        assertThat(prehashedSignature).isEqualTo(sign(1));
    }

    @Test
    void 생성_직후_키_설정이_설계값이다() {
        Map<String, Object> data = vault.read(MOUNT + "/keys/" + KEY).getData();

        assertThat(data)
            .containsEntry("type", "ed25519")
            .containsEntry("exportable", false)
            .containsEntry("allow_plaintext_backup", false)
            .containsEntry("deletion_allowed", false)
            .containsEntry("derived", false)
            .containsEntry("auto_rotate_period", 0);
    }

    private String sign(int keyVersion) {
        Map<String, Object> data = vault.write(MOUNT + "/sign/" + KEY, Map.of(
            "input", Base64.getEncoder().encodeToString(signingInput),
            "key_version", keyVersion)).getData();
        return (String) data.get("signature");
    }

    @SuppressWarnings("unchecked")
    private PublicKey publicKey(int keyVersion) throws Exception {
        Map<String, Object> data = vault.read(MOUNT + "/keys/" + KEY).getData();
        Map<String, Object> keys = (Map<String, Object>) data.get("keys");
        Map<String, Object> version = (Map<String, Object>) keys.get(String.valueOf(keyVersion));
        return parseEd25519((String) version.get("public_key"));
    }

    /** Transit 은 ed25519 공개키를 PEM 또는 raw 32바이트의 base64 로 돌려준다. 둘 다 받는다. */
    private static PublicKey parseEd25519(String publicKey) throws Exception {
        byte[] encoded;
        if (publicKey.startsWith("-----BEGIN")) {
            encoded = Base64.getDecoder().decode(
                publicKey.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        } else {
            byte[] raw = Base64.getDecoder().decode(publicKey);
            if (raw.length == 32) {
                // SubjectPublicKeyInfo 접두: SEQUENCE { AlgorithmIdentifier(id-Ed25519) , BIT STRING }
                byte[] spkiPrefix = HexFormat.of().parseHex("302a300506032b6570032100");
                encoded = new byte[spkiPrefix.length + raw.length];
                System.arraycopy(spkiPrefix, 0, encoded, 0, spkiPrefix.length);
                System.arraycopy(raw, 0, encoded, spkiPrefix.length, raw.length);
            } else {
                encoded = raw;
            }
        }
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
