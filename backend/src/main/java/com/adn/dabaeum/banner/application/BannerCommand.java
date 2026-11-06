package com.adn.dabaeum.banner.application;

import com.adn.dabaeum.banner.domain.BannerVisibility;
import java.time.Instant;
import java.util.UUID;

public record BannerCommand(
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
    UUID mobileImageFileId
) {
}
