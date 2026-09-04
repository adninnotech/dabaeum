package com.adn.dabaeum.interest.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.interest.domain.CourseInterest;
import java.util.UUID;

public interface CourseInterestApplicationService {

    CourseInterest add(AuthenticatedUserContext actor, UUID courseId);

    CourseInterestPage listMine(AuthenticatedUserContext actor, int page, int size);

    void remove(AuthenticatedUserContext actor, UUID interestId);
}
