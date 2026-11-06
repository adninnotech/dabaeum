package com.adn.dabaeum.course.domain;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CourseSessionRepository {

    void save(CourseSession session);

    Optional<CourseSession> findById(UUID sessionId);

    Optional<CourseSession> findByIdForUpdate(UUID sessionId);

    List<CourseSession> findAttendanceEligibleByCourseId(
        UUID courseId,
        Instant evaluatedAt
    );

    boolean existsAttendanceIneligibleByCourseId(UUID courseId, Instant evaluatedAt);

    Optional<CourseSession> findByCourseIdAndSessionNo(UUID courseId, int sessionNo);

    /** 과정의 회차 전부를 {@code sessionNo} 오름차순으로 돌려준다. */
    List<CourseSession> findByCourseId(UUID courseId);

    List<CourseSession> findPageByCourseId(CourseSessionPageCriteria criteria);

    long countByCourseId(UUID courseId);

    boolean update(CourseSession session);

    /** 정정용 상태 전이. update 는 종료·취소 회차를 건드리지 않으므로 따로 둔다. 현재 상태가 from 일 때만 바꾼다. */
    boolean reopen(UUID sessionId, CourseSessionStatus from, CourseSessionStatus to, Instant updatedAt);

    /** 출결이 기록됐거나 QR 이 발급된 회차가 하나라도 있으면 true. 그런 회차는 지울 수 없다. */
    boolean existsSessionDependenciesByCourseId(UUID courseId);

    /** 취소되지 않은 회차 중 가장 이른 시작 시각. 시작 시각이 있는 회차가 없으면 비어 있다. */
    Optional<Instant> findFirstActiveStartByCourseId(UUID courseId);

    /** SCHEDULED 가 아닌(진행·완료·취소된) 회차가 하나라도 있으면 true. 그런 회차는 지울 수 없다. */
    boolean existsNonScheduledByCourseId(UUID courseId);

    /** 과정의 회차를 전부 지운다. 호출 전 {@link #existsSessionDependenciesByCourseId} 로 확인할 것. */
    void deleteAllByCourseId(UUID courseId);

    /**
     * 과정의 모든 회차 {@code session_no} 에 {@code offset} 을 더한다. 재정렬 시
     * {@code (course_id, session_no)} 유니크 제약을 피해 잠시 번호대를 비켜두는 데 쓴다.
     *
     * @return 영향받은 행 수. 호출자는 이 값을 대상 회차 수와 대조해 동시 삭제로 인한
     *     불일치를 잡아내야 한다.
     */
    int shiftSessionNoByOffset(UUID courseId, int offset);

    /**
     * 재정렬 2단계: 오프셋으로 비켜둔 뒤 개별 회차에 목표 번호를 준다.
     *
     * @return 해당 회차가 갱신됐으면 true. 동시 삭제 등으로 대상이 없으면 false.
     */
    boolean updateSessionNo(UUID sessionId, int sessionNo);
}
