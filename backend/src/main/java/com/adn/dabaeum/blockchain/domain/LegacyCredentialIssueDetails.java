package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** 기존 CERT 원장 함수에 필요한 발급 정보를 손실 없이 보존한다. */
public record LegacyCredentialIssueDetails(
    String subjectHash,
    String issuerHash,
    String credentialType,
    Instant issuedAt
) {
    private static final Pattern HASH_PATTERN = Pattern.compile("^sha256:[0-9a-f]{64}$");

    public LegacyCredentialIssueDetails {
        subjectHash = requireHash(subjectHash, "subjectHash");
        issuerHash = requireHash(issuerHash, "issuerHash");
        if (!"LIFELONG_EDUCATION_COMPLETION".equals(credentialType)) {
            throw new IllegalArgumentException("credentialType is not allowed");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    private static String requireHash(String value, String name) {
        if (value == null || !HASH_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a sha256 hash");
        }
        return value;
    }
}
