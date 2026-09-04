package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.credential.domain.CredentialDocument;
import java.time.Instant;
import java.util.Objects;

/** 공개 Fabric Issue 명령 생성에 필요한 서버 소유 자료이다. */
public record CredentialFabricIssuanceContext(
    CredentialDocument document,
    String issuerIdentifier,
    String subjectIdentifier,
    Instant issuedAt
) {
    public CredentialFabricIssuanceContext {
        Objects.requireNonNull(document, "document");
        issuerIdentifier = requireText(issuerIdentifier, "issuerIdentifier");
        subjectIdentifier = requireText(subjectIdentifier, "subjectIdentifier");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
