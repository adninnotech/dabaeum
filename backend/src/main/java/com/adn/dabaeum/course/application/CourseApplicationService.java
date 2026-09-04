package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import java.util.UUID;

public interface CourseApplicationService {

    Course create(
        CreateCourseCommand command,
        AuthenticatedUserContext context
    );

    Course get(UUID courseId);

    CoursePage list(ListCoursesQuery query);

    Course update(
        UpdateCourseCommand command,
        AuthenticatedUserContext context
    );

    Course publish(UUID courseId, AuthenticatedUserContext context);

    Course close(UUID courseId, AuthenticatedUserContext context);

    /** 관리자 정정: 잘못 마감한 모집을 사유와 함께 다시 연다. */
    Course reopen(UUID courseId, String reason, AuthenticatedUserContext context);
}
