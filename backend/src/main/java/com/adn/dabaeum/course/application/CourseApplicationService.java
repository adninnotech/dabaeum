package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseView;
import java.util.UUID;

public interface CourseApplicationService {

    Course create(
        CreateCourseCommand command,
        AuthenticatedUserContext context
    );

    /**
     * DRAFT 강좌는 그 강좌를 관리할 수 있는 사용자(PLATFORM_ADMIN, 그 기관 소속 INSTITUTION_ADMIN,
     * 그 기관 소속 INSTRUCTOR)에게만 보인다. 그 밖의 사용자에게는 COURSE_NOT_FOUND — 존재 자체를
     * 알려주지 않는다. DRAFT 가 아닌 강좌는 지금까지처럼 누구나 볼 수 있다. 목록(list)과 같은 기준.
     * {@code context} 가 null 이면 로그인하지 않은 공개 조회다 — 학습자와 같이 DRAFT 가 보이지 않는다.
     */
    CourseView get(UUID courseId, AuthenticatedUserContext context);

    /**
     * DRAFT 강좌는 PLATFORM_ADMIN 에게는 전부, 그 기관 소속 INSTITUTION_ADMIN·INSTRUCTOR 에게는
     * 자기 기관 것만 보인다. 그 밖의 사용자(학습자 포함)에게는 {@code status=DRAFT} 를 명시해도
     * 나오지 않는다. {@code context} 가 null(비로그인 공개 조회)이어도 같다.
     */
    CoursePage list(ListCoursesQuery query, AuthenticatedUserContext context);

    Course update(
        UpdateCourseCommand command,
        AuthenticatedUserContext context
    );

    Course publish(UUID courseId, AuthenticatedUserContext context);

    Course close(UUID courseId, AuthenticatedUserContext context);

    /** 관리자 정정: 잘못 마감한 모집을 사유와 함께 다시 연다. */
    Course reopen(UUID courseId, String reason, AuthenticatedUserContext context);

    /**
     * 임시저장(DRAFT) 강좌를 지운다(soft delete). 게시된 적이 없어 수강·출결·수료가 붙지 않은 강좌만
     * 대상이다. 권한은 수정과 같고, DRAFT 가 아니면 COURSE_STATUS_CONFLICT.
     */
    void deleteDraft(UUID courseId, AuthenticatedUserContext context);
}
