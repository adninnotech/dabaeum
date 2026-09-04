package com.adn.dabaeum.review.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.review.domain.CourseReview;
import java.util.UUID;

public interface CourseReviewApplicationService {

    CourseReview create(
        AuthenticatedUserContext actor, UUID courseId, int rating, String content);

    CourseReviewPage listMine(AuthenticatedUserContext actor, int page, int size);

    CourseReviewPage listByCourse(UUID courseId, int page, int size);
}
