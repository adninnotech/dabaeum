package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionPageCriteria;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseSessionMyBatisRepository implements CourseSessionRepository {

    private final CourseSessionMapper mapper;

    public CourseSessionMyBatisRepository(CourseSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CourseSession session) {
        mapper.insert(toRow(session));
    }

    @Override
    public Optional<CourseSession> findById(UUID sessionId) {
        return Optional.ofNullable(mapper.selectById(sessionId)).map(this::toDomain);
    }

    @Override
    public Optional<CourseSession> findByIdForUpdate(UUID sessionId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(sessionId)).map(this::toDomain);
    }

    @Override
    public List<CourseSession> findAttendanceEligibleByCourseId(
        UUID courseId,
        Instant evaluatedAt
    ) {
        return mapper.selectAttendanceEligibleByCourseId(courseId, evaluatedAt)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsAttendanceIneligibleByCourseId(UUID courseId, Instant evaluatedAt) {
        return mapper.existsAttendanceIneligibleByCourseId(courseId, evaluatedAt);
    }

    @Override
    public Optional<CourseSession> findByCourseIdAndSessionNo(
        UUID courseId,
        int sessionNo
    ) {
        return Optional.ofNullable(mapper.selectByCourseIdAndSessionNo(courseId, sessionNo))
            .map(this::toDomain);
    }

    @Override
    public List<CourseSession> findByCourseId(UUID courseId) {
        return mapper.selectByCourseId(courseId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CourseSession> findPageByCourseId(CourseSessionPageCriteria criteria) {
        return mapper.selectPage(criteria).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByCourseId(UUID courseId) {
        return mapper.countByCourseId(courseId);
    }

    @Override
    public boolean update(CourseSession session) {
        return mapper.update(toRow(session)) == 1;
    }

    @Override
    public boolean reopen(UUID sessionId, CourseSessionStatus from, CourseSessionStatus to, Instant updatedAt) {
        return mapper.reopen(sessionId, from.name(), to.name(), updatedAt) == 1;
    }

    @Override
    public boolean existsSessionDependenciesByCourseId(UUID courseId) {
        return mapper.existsSessionDependenciesByCourseId(courseId);
    }

    @Override
    public boolean existsNonScheduledByCourseId(UUID courseId) {
        return mapper.existsNonScheduledByCourseId(courseId);
    }

    @Override
    public Optional<Instant> findFirstActiveStartByCourseId(UUID courseId) {
        return Optional.ofNullable(mapper.selectFirstActiveStartByCourseId(courseId));
    }

    @Override
    public void deleteAllByCourseId(UUID courseId) {
        mapper.deleteAllByCourseId(courseId);
    }

    @Override
    public int shiftSessionNoByOffset(UUID courseId, int offset) {
        return mapper.shiftSessionNoByOffset(courseId, offset);
    }

    @Override
    public boolean updateSessionNo(UUID sessionId, int sessionNo) {
        return mapper.updateSessionNo(sessionId, sessionNo) == 1;
    }

    private CourseSessionRow toRow(CourseSession session) {
        return new CourseSessionRow(
            session.id(),
            session.courseId(),
            session.sessionNo(),
            session.startsAt(),
            session.endsAt(),
            session.location(),
            session.attendanceOpensAt(),
            session.attendanceClosesAt(),
            session.status().name(),
            session.createdAt(),
            session.updatedAt(),
            session.topic()
        );
    }

    private CourseSession toDomain(CourseSessionRow row) {
        return new CourseSession(
            row.id(),
            row.courseId(),
            row.sessionNo(),
            row.startsAt(),
            row.endsAt(),
            row.location(),
            row.attendanceOpensAt(),
            row.attendanceClosesAt(),
            CourseSessionStatus.valueOf(row.status()),
            row.createdAt(),
            row.updatedAt(),
            row.topic()
        );
    }
}
