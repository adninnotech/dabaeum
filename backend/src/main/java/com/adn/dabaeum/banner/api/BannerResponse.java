package com.adn.dabaeum.banner.api;

import com.adn.dabaeum.banner.domain.BannerVisibility;
import java.time.Instant;
import java.util.UUID;

public record BannerResponse(
    UUID id,
    String badgeText,
    String title,
    String subtitle,
    String buttonText,
    String buttonLink,
    BannerVisibility visibility,
    Instant startAt,
    Instant endAt,
    int sortOrder,
    UUID pcImageFileId,
    UUID mobileImageFileId,
    Instant createdAt,
    Instant updatedAt
) {
}
