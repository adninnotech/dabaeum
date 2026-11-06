package com.adn.dabaeum.support.api;

import java.time.Instant;

public record NoticeUpdateRequest(
    String title,
    String body,
    String audience,
    String status,
    /** 미래로 주면 예약 게시다 — 게시 상태여도 공개 목록에는 그 시각 이후에 나온다. */
    Instant publishedAt
) {
}
