package com.adn.dabaeum.notification.api;

import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record NotificationPageData(
    List<NotificationResponse> notifications,
    long unreadCount,
    PageMeta page
) {
}
