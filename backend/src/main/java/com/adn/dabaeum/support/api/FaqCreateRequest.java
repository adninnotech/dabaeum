package com.adn.dabaeum.support.api;

public record FaqCreateRequest(
    String question,
    String answer,
    /** 생략하면 0으로 들어간다. */
    Integer sortOrder
) {
}
