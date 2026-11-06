package com.adn.dabaeum.banner.api;

import com.adn.dabaeum.banner.domain.BannerVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * 배너 등록·수정 요청.
 *
 * <p>계약서에는 {@code BannerCreateRequest} 와 {@code BannerUpdateRequest} 로 나뉘어 있지만
 * 배너는 부분 수정이 없고 두 본문이 같은 모양이라 자바에서는 한 레코드를 함께 쓴다.
 */
public record BannerRequest(
    @Size(max = 60) String badgeText,
    @NotBlank @Size(max = 60) String title,
    @Size(max = 60) String subtitle,
    @Size(max = 30) String buttonText,
    @Size(max = 1000) String buttonLink,
    BannerVisibility visibility,
    Instant startAt,
    Instant endAt,
    Integer sortOrder,
    UUID pcImageFileId,
    UUID mobileImageFileId
) {
}
