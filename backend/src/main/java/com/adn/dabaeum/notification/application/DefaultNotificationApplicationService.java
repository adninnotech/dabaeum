package com.adn.dabaeum.notification.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.notification.domain.Notification;
import com.adn.dabaeum.notification.domain.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultNotificationApplicationService
    implements NotificationApplicationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public DefaultNotificationApplicationService(
        NotificationRepository notificationRepository,
        Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage listMine(
        AuthenticatedUserContext actor, boolean unreadOnly, int page, int size
    ) {
        int offset = Math.multiplyExact(page, size);
        var data = notificationRepository.findByUserId(
            actor.userId(), unreadOnly, size, offset);
        long totalElements = notificationRepository.countByUserId(
            actor.userId(), unreadOnly);
        long unreadCount = notificationRepository.countUnread(actor.userId());
        int totalPages = totalElements == 0
            ? 0 : (int) ((totalElements + size - 1) / size);
        return new NotificationPage(
            data, page, size, totalElements, totalPages, unreadCount);
    }

    @Override
    @Transactional
    public Notification markRead(AuthenticatedUserContext actor, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(this::notFound);
        if (!Objects.equals(notification.userId(), actor.userId())) {
            throw notFound();
        }
        if (notification.readAt() != null) {
            return notification;
        }
        Instant readAt = clock.instant();
        if (!notificationRepository.markRead(notificationId, actor.userId(), readAt)) {
            throw notFound();
        }
        return new Notification(
            notification.id(), notification.userId(), notification.title(),
            notification.body(), readAt, notification.createdAt());
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOTIFICATION_NOT_FOUND,
            "Notification not found");
    }
}
