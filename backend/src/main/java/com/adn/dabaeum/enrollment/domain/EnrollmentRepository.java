package com.adn.dabaeum.enrollment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    void save(Enrollment enrollment);

    Optional<Enrollment> findById(UUID enrollmentId);

    Optional<Enrollment> findByIdForUpdate(UUID enrollmentId);

    /** 일괄 승인·반려가 권한을 한 번에 검사하려고 대상 전부를 미리 읽는다. */
    List<Enrollment> findAllByIds(List<UUID> ids);

    List<UUID> findInstitutionIdsByUserId(UUID userId);

    Optional<Enrollment> findActiveByCourseIdAndUserId(UUID courseId, UUID userId);

    /** 과정의 살아 있는 수강 전부(APPLIED·WAITLISTED·APPROVED). 과정 취소가 쓴다. */
    List<Enrollment> findActiveByCourseId(UUID courseId);

    /** 신청자 정보·대기 순번까지 조인해 담는다(횡단 이슈 X-2). */
    List<EnrollmentListItem> findEnrollmentListByCourseId(EnrollmentPageCriteria criteria);

    long countByCourseId(UUID courseId);

    long countApprovedByCourseId(UUID courseId);

    /** 출석(PRESENT·LATE)으로 기록된 회차가 하나라도 있는가. 결석·공결은 출석으로 보지 않는다. */
    boolean hasAttendance(UUID enrollmentId);

    /** 이수가 평가(ELIGIBLE)되었거나 확정(COMPLETED)되었는가. */
    boolean hasCompletionDecision(UUID enrollmentId);

    boolean updateState(Enrollment updated, EnrollmentStatus expectedStatus);
}
