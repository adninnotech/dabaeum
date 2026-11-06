package com.adn.dabaeum.banner.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record BannerRow(
    UUID id,
    String badgeText,
    String title,
    String subtitle,
    String buttonText,
    String buttonLink,
    String visibility,
    Instant startAt,
    Instant endAt,
    Integer sortOrder,
    UUID pcImageFileId,
    UUID mobileImageFileId,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
}
