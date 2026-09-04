package com.adn.dabaeum.support.api;

public record NoticeUpdateRequest(
    String title,
    String body,
    String audience,
    String status
) {
}
