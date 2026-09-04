package com.adn.dabaeum.notification.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.notification.domain.Notification;
import java.util.UUID;

public interface NotificationApplicationService {

    NotificationPage listMine(
        AuthenticatedUserContext actor, boolean unreadOnly, int page, int size);

    Notification markRead(AuthenticatedUserContext actor, UUID notificationId);
}
