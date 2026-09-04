package com.adn.dabaeum.credential.domain;

import java.nio.charset.StandardCharsets;

public record CredentialDocument(String canonicalJson) {

    public CredentialDocument {
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new IllegalArgumentException("canonicalJson is required");
        }
    }

    public byte[] utf8Bytes() {
        return canonicalJson.getBytes(StandardCharsets.UTF_8);
    }
}
