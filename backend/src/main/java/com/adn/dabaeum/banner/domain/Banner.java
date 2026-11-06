package com.adn.dabaeum.banner.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** B-10 배너 관리 / E-1 홈 최상단 캐러셀에 쓰는 배너 한 장. */
public record Banner(
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
    Instant updatedAt,
    Instant deletedAt
) {

    public Banner {
        Objects.requireNonNull(id, "id");
        String trimmedTitle = title == null ? null : title.trim();
        if (trimmedTitle == null || trimmedTitle.isBlank() || trimmedTitle.length() > 60) {
            throw new IllegalArgumentException("title must be 1..60 characters");
        }
        title = trimmedTitle;
        Objects.requireNonNull(visibility, "visibility");
        // 기간 공개인데 기간이 없으면 "언제 보이는가"에 답이 없다. DB CHECK
        // ck_tb_banners_scheduled_period 와 같은 규칙을 도메인에도 둬서, 서비스를 거치지 않는
        // 경로에서도 깨진 배너가 만들어지지 않게 한다.
        if (visibility == BannerVisibility.SCHEDULED && (startAt == null || endAt == null)) {
            throw new IllegalArgumentException("SCHEDULED banner requires startAt and endAt");
        }
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("startAt must not be after endAt");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * 지금 공개 목록에 보이는 배너인지.
     *
     * <p>공개 목록 SQL 은 이 조건을 다시 쓰지 않는다. SQL {@code WHERE} 절에만 있으면 조건이
     * 맞는지 보려고 DB 를 띄워야 하고, 관리자 목록과 공개 목록이 서로 다른 조건을 쓰게 되기
     * 쉽다. 기간 경계는 시작·종료 양쪽 모두 포함이다.
     */
    public boolean isVisibleAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return switch (visibility) {
            case PUBLIC -> true;
            case PRIVATE -> false;
            case SCHEDULED -> !at.isBefore(startAt) && !at.isAfter(endAt);
        };
    }
}
