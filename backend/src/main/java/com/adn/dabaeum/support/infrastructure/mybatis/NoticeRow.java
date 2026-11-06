package com.adn.dabaeum.support.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record NoticeRow(
    UUID id,
    String title,
    String body,
    String audience,
    String status,
    Instant publishedAt,
    UUID createdBy,
    /** tb_users 조인으로만 채워지는 조회 전용 필드. insert/update 에는 쓰지 않는다. */
    String authorName,
    Instant createdAt,
    Instant updatedAt
) {
}
