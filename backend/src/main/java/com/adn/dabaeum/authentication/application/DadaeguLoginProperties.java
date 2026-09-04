package com.adn.dabaeum.authentication.application;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 다대구 QR 로그인 연계 설정. privateKey 는 포털에서 받은 RSA 개인키(PEM 또는 Base64 PKCS#8). */
@ConfigurationProperties(prefix = "dabaeum.security.dadaegu")
public record DadaeguLoginProperties(String siteId, String privateKey) {

    public DadaeguLoginProperties {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId is required");
        }
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalArgumentException("privateKey is required");
        }
        siteId = siteId.trim();
        privateKey = privateKey
            .replaceAll("-----[^-]+-----", "")
            .replaceAll("\\s", "");
        rsaPrivateKey(privateKey);
    }

    public PrivateKey rsaPrivateKey() {
        return rsaPrivateKey(privateKey);
    }

    private static PrivateKey rsaPrivateKey(String base64) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                "privateKey must be a Base64 PKCS#8 RSA private key", exception
            );
        }
    }
}
