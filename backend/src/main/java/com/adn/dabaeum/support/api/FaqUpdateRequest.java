package com.adn.dabaeum.support.api;

public record FaqUpdateRequest(
    String question,
    String answer,
    Integer sortOrder
) {
}
