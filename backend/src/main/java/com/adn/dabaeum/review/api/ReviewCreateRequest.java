package com.adn.dabaeum.review.api;

public record ReviewCreateRequest(
    Integer rating,
    String content
) {
}
