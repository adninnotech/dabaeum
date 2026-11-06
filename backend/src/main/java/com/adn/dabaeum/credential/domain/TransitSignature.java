package com.adn.dabaeum.credential.domain;

/** Transit sign 응답 {@code vault:v{keyVersion}:{base64}} 를 푼 값 (설계서 §7.3). */
public record TransitSignature(int keyVersion, byte[] signature) {

    public TransitSignature {
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        if (signature == null || signature.length != 64) {
            throw new IllegalArgumentException("Ed25519 signature must be 64 bytes");
        }
        signature = signature.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
