package com.adn.dabaeum.credential.api;

/** 비동기 자격증명 재발급 요청 본문이다. */
public record CredentialReissueRequest(String reason, String validUntil) {
}
