package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseSession;
import java.util.List;
import java.util.UUID;

public interface CourseSessionApplicationService {

    CourseSessionPage list(ListCourseSessionsQuery query);

    CourseSession create(
        CreateCourseSessionCommand command,
        AuthenticatedUserContext context
    );

    /** 반복 규칙 또는 직접 입력 목록을 한 트랜잭션에서 전부 만든다. */
    List<CourseSession> createBulk(
        UUID courseId,
        CreateCourseSessionBulkCommand command,
        AuthenticatedUserContext context
    );

    CourseSession get(UUID sessionId);

    CourseSession update(
        UpdateCourseSessionCommand command,
        AuthenticatedUserContext context
    );

    /** 관리자 정정: 잘못 마감한 회차를 사유와 함께 다시 연다. */
    CourseSession reopen(UUID sessionId, String reason, AuthenticatedUserContext context);

    /**
     * 드래그로 바뀐 순서대로 회차 번호를 다시 매긴다. {@code sessionIds} 는 과정의 회차
     * 전부를 정확히 한 번씩 포함해야 한다 — 일부만 보내면 화면이 보고 있지 않은 회차의
     * 번호가 조용히 바뀌므로 거부한다.
     */
    List<CourseSession> reorder(
        UUID courseId,
        List<UUID> sessionIds,
        AuthenticatedUserContext context
    );
}
