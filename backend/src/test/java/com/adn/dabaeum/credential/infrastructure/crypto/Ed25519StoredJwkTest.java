package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 버전 행에 보존한 JWK 를 검증·발급자 문서에 되살린다 (설계서 §9.2). */
class Ed25519StoredJwkTest {

    private static final String KEY_ID =
        "https://vc.example.test/api/v1/vc/issuers/20000000-0000-0000-0000-000000000002#v2";
    /** RFC 8037 A.2 의 Ed25519 공개키 x (32바이트). */
    private static final String RFC_X = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo";

    private final Ed25519PublicJwkFactory factory = new Ed25519PublicJwkFactory();

    @Test
    void jsonb_정규화로_순서와_공백이_바뀐_JWK_도_같은_공개키로_되돌린다() throws Exception {
        PublicKey key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        String x = factory.create("ignored", key).get("x");
        String jsonbText = "{\"x\": \"" + x + "\", \"alg\": \"EdDSA\", \"crv\": \"Ed25519\", "
            + "\"kty\": \"OKP\", \"use\": \"sig\"}";

        assertThat(factory.publicKey(jsonbText).getEncoded()).isEqualTo(key.getEncoded());
        assertThat(factory.publish(KEY_ID, jsonbText)).containsExactly(
            entry("kid", KEY_ID), entry("kty", "OKP"), entry("crv", "Ed25519"),
            entry("alg", "EdDSA"), entry("use", "sig"), entry("x", x));
    }

    @Test
    void Ed25519_서명_공개키가_아닌_저장값은_거부한다() {
        List<String> rejected = List.of(
            "",
            "not json",
            "[]",
            jwk("OKP", "Ed25519", "EdDSA", "sig", null),
            jwk("OKP", "X25519", "EdDSA", "sig", RFC_X),
            jwk("EC", "Ed25519", "EdDSA", "sig", RFC_X),
            jwk("OKP", "Ed25519", "ES256", "sig", RFC_X),
            jwk("OKP", "Ed25519", "EdDSA", "enc", RFC_X),
            jwk("OKP", "Ed25519", "EdDSA", "sig", "AA"),
            jwk("OKP", "Ed25519", "EdDSA", "sig", RFC_X + "="),
            jwk("OKP", "Ed25519", "EdDSA", "sig", RFC_X).replace("}", ",\"d\":\"AA\"}"),
            jwk("OKP", "Ed25519", "EdDSA", "sig", RFC_X).replace("{", "{\"kid\":\"k\","));

        for (String storedJwk : rejected) {
            assertThatThrownBy(() -> factory.publicKey(storedJwk))
                .as(storedJwk).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> factory.publish(KEY_ID, storedJwk))
                .as(storedJwk).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> factory.publish(" ", jwk("OKP", "Ed25519", "EdDSA", "sig", RFC_X)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static String jwk(String kty, String crv, String alg, String use, String x) {
        String body = "\"kty\":\"" + kty + "\",\"crv\":\"" + crv + "\",\"alg\":\"" + alg
            + "\",\"use\":\"" + use + "\"";
        return "{" + body + (x == null ? "" : ",\"x\":\"" + x + "\"") + "}";
    }
}
