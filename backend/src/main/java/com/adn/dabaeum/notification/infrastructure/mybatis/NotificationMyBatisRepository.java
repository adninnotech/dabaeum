package com.adn.dabaeum.notification.infrastructure.mybatis;

import com.adn.dabaeum.notification.domain.Notification;
import com.adn.dabaeum.notification.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class NotificationMyBatisRepository implements NotificationRepository {

    private final NotificationMapper mapper;

    public NotificationMyBatisRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Notification notification) {
        mapper.insert(new NotificationRow(
            notification.id(), notification.userId(), notification.title(),
            notification.body(), notification.readAt(), notification.createdAt()));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Notification> findByUserId(
        UUID userId, boolean unreadOnly, int limit, int offset
    ) {
        return mapper.selectByUserId(userId, unreadOnly, limit, offset)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public long countByUserId(UUID userId, boolean unreadOnly) {
        return mapper.countByUserId(userId, unreadOnly);
    }

    @Override
    public long countUnread(UUID userId) {
        return mapper.countUnread(userId);
    }

    @Override
    public boolean markRead(UUID id, UUID userId, Instant readAt) {
        return mapper.markRead(id, userId, readAt) == 1;
    }

    private Notification toDomain(NotificationRow row) {
        return new Notification(
            row.id(), row.userId(), row.title(), row.body(),
            row.readAt(), row.createdAt());
    }
}
