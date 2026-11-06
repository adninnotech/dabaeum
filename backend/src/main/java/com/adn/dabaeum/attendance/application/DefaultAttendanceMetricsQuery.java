package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultAttendanceMetricsQuery implements AttendanceMetricsQuery {

    private final CourseSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;

    public DefaultAttendanceMetricsQuery(
        CourseSessionRepository sessionRepository,
        AttendanceRepository attendanceRepository,
        EnrollmentRepository enrollmentRepository
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository);
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository);
    }

    @Override
    public AttendanceMetrics calculate(UUID enrollmentId, Instant evaluatedAt) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new IllegalArgumentException("enrollment not found"));
        Map<UUID, Attendance> bySession = attendanceRepository.findByEnrollment(enrollmentId)
            .stream().collect(Collectors.toMap(
                Attendance::sessionId, Function.identity(), (left, right) -> right));
        // 분모는 학습자가 출석할 수 있었던 회차로 한정한다. 수강 승인 전에 시작한 회차는
        // 출결 기록이 있을 때만 세고, 기록이 없으면 결석이 아니라 대상 밖으로 본다.
        // 그렇지 않으면 중도 합류자나 뒤늦게 추가된 회차가 소급 결석으로 잡힌다.
        Instant approvedAt = enrollment.approvedAt();
        var sessions = sessionRepository.findByCourseId(enrollment.courseId()).stream()
            .filter(session -> counted(session, bySession.containsKey(session.id()), evaluatedAt))
            .filter(session -> bySession.containsKey(session.id())
                || approvedAt == null
                || !session.startsAt().isBefore(approvedAt))
            .toList();

        int present = 0;
        int late = 0;
        int absent = 0;
        int excused = 0;
        long completedMinutes = 0;
        for (CourseSession session : sessions) {
            Attendance attendance = bySession.get(session.id());
            AttendanceStatus status = attendance == null
                ? AttendanceStatus.ABSENT : attendance.status();
            switch (status) {
                case PRESENT -> {
                    present++;
                    completedMinutes += minutes(session);
                }
                case LATE -> {
                    late++;
                    completedMinutes += minutes(session);
                }
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
            }
        }
        if (completedMinutes > Integer.MAX_VALUE) {
            throw new IllegalStateException("completed minutes overflow");
        }
        int total = sessions.size();
        BigDecimal rate = AttendanceRateRule.rate(present, late, excused, total);
        return new AttendanceMetrics(
            enrollmentId, total, present, late, absent, excused, rate,
            (int) completedMinutes);
    }

    /**
     * 센 회차: 취소되지 않았고 시간이 있는 회차 중 종료됐거나 출결 창이 지난 회차, 그리고 진행 중·시작 전이라도
     * 이 수강에 출결 기록이 이미 있는 회차. 진행 중 회차의 기록을 빼면 출석률이 0% 로 보였다(QA ATT-09).
     */
    private static boolean counted(CourseSession session, boolean hasRecord, Instant evaluatedAt) {
        if (session.status() == CourseSessionStatus.CANCELLED || session.startsAt() == null) {
            return false;
        }
        return hasRecord
            || session.status() == CourseSessionStatus.COMPLETED
            || !session.attendanceClosesAtOrEnd().isAfter(evaluatedAt);
    }

    private long minutes(CourseSession session) {
        return Duration.between(session.startsAt(), session.endsAt()).toMinutes();
    }
}
