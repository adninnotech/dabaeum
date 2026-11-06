package com.adn.dabaeum.usage.domain;

import java.util.UUID;

/** 배너별 클릭한 사람 수. 지운 배너도 기간 안 클릭이 있으면 나온다. */
public record BannerClickCount(UUID bannerId, String title, boolean deleted, long clickers) {
}
