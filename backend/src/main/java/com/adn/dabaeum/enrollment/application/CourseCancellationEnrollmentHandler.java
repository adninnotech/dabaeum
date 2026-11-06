package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.course.application.CourseCancelledListener;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 과정이 취소되면 살아 있는 수강을 모두 닫는다. 승인된 수강은 철회(WITHDRAWN), 신청·대기는 취소(CANCELLED)
 * 가 되고 취소 사유에 과정 취소를 남긴다. 이수 판정(평가·확정)이 있는 수강은 수료·수료증과 어긋나므로
 * 건드리지 않는다(철회 규칙과 같다). 알림은 알림 생성 기능이 생기면 여기서 보낸다.
 */
@Component
public class CourseCancellationEnrollmentHandler implements CourseCancelledListener {

    static final String REASON = "과정 취소";

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseCancellationEnrollmentHandler.class);

    private final EnrollmentRepository enrollmentRepository;

    public CourseCancellationEnrollmentHandler(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository, "enrollmentRepository");
    }

    @Override
    public void onCourseCancelled(UUID courseId, UUID institutionId, Instant cancelledAt) {
        for (Enrollment enrollment : enrollmentRepository.findActiveByCourseId(courseId)) {
            if (enrollmentRepository.hasCompletionDecision(enrollment.id())) {
                LOGGER.info("course_cancelled_enrollment_kept courseId={} enrollmentId={} reason=completion_decision",
                    courseId, enrollment.id());
                continue;
            }
            Enrollment closed = close(enrollment, cancelledAt);
            if (!enrollmentRepository.updateState(closed, enrollment.status())) {
                LOGGER.warn("course_cancelled_enrollment_skipped courseId={} enrollmentId={} expectedStatus={}",
                    courseId, enrollment.id(), enrollment.status());
            }
        }
    }

    private static Enrollment close(Enrollment e, Instant at) {
        return e.status() == EnrollmentStatus.APPROVED
            ? new Enrollment(e.id(), e.courseId(), e.userId(), e.appliedBy(), e.applicationType(),
                EnrollmentStatus.WITHDRAWN, e.appliedAt(), e.approvedAt(), null, null, at, null, REASON,
                e.createdAt(), at)
            : new Enrollment(e.id(), e.courseId(), e.userId(), e.appliedBy(), e.applicationType(),
                EnrollmentStatus.CANCELLED, e.appliedAt(), null, null, at, null, null, REASON,
                e.createdAt(), at);
    }
}
