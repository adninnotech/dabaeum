package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public interface LearningBadgeApplicationService {

    LearningBadge issue(IssueLearningBadgeCommand command);

    LearningBadge get(UUID badgeId, AuthenticatedUserContext actor);

    LearningBadgePage listByUser(ListUserBadgesQuery query);
}
