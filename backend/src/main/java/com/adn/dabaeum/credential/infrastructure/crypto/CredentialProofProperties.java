package com.adn.dabaeum.credential.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.credential-proof")
public class CredentialProofProperties {

    public static final String DEVELOPMENT_KEY_ID =
        "urn:dabaeum:credential-key:development-1";

    private String keyId = DEVELOPMENT_KEY_ID;
    private String privateKeyPath = "";
    private String publicKeyPath = "";

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        if (!DEVELOPMENT_KEY_ID.equals(keyId)) {
            throw new IllegalArgumentException("credential proof key id is invalid");
        }
        this.keyId = keyId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath == null ? "" : publicKeyPath;
    }
}
