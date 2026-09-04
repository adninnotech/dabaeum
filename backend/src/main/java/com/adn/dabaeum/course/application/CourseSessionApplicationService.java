package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseSession;
import java.util.UUID;

public interface CourseSessionApplicationService {

    CourseSessionPage list(ListCourseSessionsQuery query);

    CourseSession create(
        CreateCourseSessionCommand command,
        AuthenticatedUserContext context
    );

    CourseSession get(UUID sessionId);

    CourseSession update(
        UpdateCourseSessionCommand command,
        AuthenticatedUserContext context
    );

    /** 관리자 정정: 잘못 마감한 회차를 사유와 함께 다시 연다. */
    CourseSession reopen(UUID sessionId, String reason, AuthenticatedUserContext context);
}
