package com.adn.dabaeum.credential.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * JWS signing input 의 두 조각 (설계서 §7.2, §7.4). 서명 전에 그대로 커밋하고, 재시도는 이 두 문자열을
 * 다시 만들지 않고 서명한다.
 */
public record CredentialSigningInput(String encodedHeader, String encodedPayload) {

    public static final String MEDIA_TYPE = "application/vc+jwt";
    private static final Pattern BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final int ED25519_SIGNATURE_BYTES = 64;

    public CredentialSigningInput {
        requireSegment(encodedHeader, "encodedHeader");
        requireSegment(encodedPayload, "encodedPayload");
    }

    /** OpenBao sign 요청의 input. 해시하지 않은 원문이다. */
    public byte[] bytes() {
        return (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
    }

    /** payload 조각의 원문. 버전 거부 뒤 헤더만 다시 만들 때 쓴다. */
    public CredentialDocument document() {
        return new CredentialDocument(
            new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8));
    }

    public SignedCredentialEnvelope envelope(byte[] signature) {
        Objects.requireNonNull(signature, "signature");
        if (signature.length != ED25519_SIGNATURE_BYTES) {
            throw new IllegalArgumentException("Ed25519 signature must be 64 bytes");
        }
        return new SignedCredentialEnvelope(MEDIA_TYPE, encodedHeader + "." + encodedPayload + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    private static void requireSegment(String value, String name) {
        if (value == null || !BASE64URL.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be unpadded base64url");
        }
    }
}
