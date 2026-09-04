package com.adn.dabaeum.notification.api;

import com.adn.dabaeum.common.api.ApiMeta;

public record NotificationPageResponse(
    NotificationPageData data,
    ApiMeta meta
) {
}
