package com.adn.dabaeum.notification.application;

import com.adn.dabaeum.notification.domain.Notification;
import java.util.List;

public record NotificationPage(
    List<Notification> data,
    int page,
    int size,
    long totalElements,
    int totalPages,
    long unreadCount
) {
}
