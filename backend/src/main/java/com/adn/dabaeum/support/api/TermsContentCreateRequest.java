package com.adn.dabaeum.support.api;

public record TermsContentCreateRequest(
    String type,
    String title,
    String body,
    String version
) {
}
