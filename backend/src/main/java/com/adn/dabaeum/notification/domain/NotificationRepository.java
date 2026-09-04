package com.adn.dabaeum.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByUserId(
        UUID userId, boolean unreadOnly, int limit, int offset);

    long countByUserId(UUID userId, boolean unreadOnly);

    long countUnread(UUID userId);

    boolean markRead(UUID id, UUID userId, java.time.Instant readAt);
}
