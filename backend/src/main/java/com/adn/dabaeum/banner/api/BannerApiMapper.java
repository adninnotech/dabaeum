package com.adn.dabaeum.banner.api;

import com.adn.dabaeum.banner.application.BannerCommand;
import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BannerApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public BannerApiMapper(Clock clock) {
        this.clock = clock;
    }

    public BannerCommand toCommand(BannerRequest request) {
        return new BannerCommand(
            request.badgeText(),
            request.title(),
            request.subtitle(),
            request.buttonText(),
            request.buttonLink(),
            // 공개는 명시적이어야 한다. 값을 안 주면 비공개로 만든다.
            request.visibility() == null ? BannerVisibility.PRIVATE : request.visibility(),
            request.startAt(),
            request.endAt(),
            request.sortOrder() == null ? 0 : request.sortOrder(),
            request.pcImageFileId(),
            request.mobileImageFileId()
        );
    }

    public ApiResponse<List<BannerResponse>> toListResponse(
        List<Banner> banners,
        String requestId
    ) {
        return new ApiResponse<>(
            banners.stream().map(this::toResponse).toList(), apiMeta(requestId));
    }

    public ApiResponse<BannerResponse> toResponse(Banner banner, String requestId) {
        return new ApiResponse<>(toResponse(banner), apiMeta(requestId));
    }

    public BannerResponse toResponse(Banner banner) {
        return new BannerResponse(
            banner.id(), banner.badgeText(), banner.title(), banner.subtitle(),
            banner.buttonText(), banner.buttonLink(), banner.visibility(),
            banner.startAt(), banner.endAt(), banner.sortOrder(),
            banner.pcImageFileId(), banner.mobileImageFileId(),
            banner.createdAt(), banner.updatedAt());
    }

    public ApiMeta meta(String requestId) {
        return apiMeta(requestId);
    }

    private ApiMeta apiMeta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
