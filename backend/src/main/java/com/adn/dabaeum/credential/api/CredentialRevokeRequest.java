package com.adn.dabaeum.credential.api;

/** 비동기 자격증명 폐기 요청 본문이다. */
public record CredentialRevokeRequest(String reason) {
}
