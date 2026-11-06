package com.adn.dabaeum.recommendation.api;

import com.adn.dabaeum.course.api.CourseResponse;
import java.util.UUID;

/** yaml {@code RecommendedCourse}. impressionId 는 로그인 학습자에게만 있고 클릭 기록에 쓴다. */
public record RecommendedCourseResponse(
    UUID impressionId,
    CourseResponse course,
    int matchLevel,
    RecommendationReasonResponse reason
) {
}
