package com.adn.dabaeum.support.api;

public record NoticeCreateRequest(
    String title,
    String body,
    String audience,
    String status
) {
}
