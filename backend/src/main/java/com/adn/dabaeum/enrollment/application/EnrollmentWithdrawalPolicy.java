package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.enrollment.config.EnrollmentWithdrawalProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 학습자 본인 철회 마감 시각. 수업 시작은 취소되지 않은 첫 회차의 시작 시각이고, 회차가 없으면
 * 과정 startDate 의 서울 00:00 이다. 둘 다 없으면(게시 전 과정) 마감이 없다.
 */
@Component
public class EnrollmentWithdrawalPolicy {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CourseSessionRepository sessionRepository;
    private final EnrollmentWithdrawalProperties properties;

    public EnrollmentWithdrawalPolicy(
        CourseSessionRepository sessionRepository,
        EnrollmentWithdrawalProperties properties
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public Optional<Instant> selfWithdrawDeadline(Course course) {
        return courseStart(course).map(start -> start.minus(properties.selfCutoff()));
    }

    private Optional<Instant> courseStart(Course course) {
        Optional<Instant> firstSession = sessionRepository.findFirstActiveStartByCourseId(course.id());
        if (firstSession.isPresent()) {
            return firstSession;
        }
        if (course.startDate() == null) {
            return Optional.empty();
        }
        return Optional.of(course.startDate().atStartOfDay(SEOUL).toInstant());
    }
}
