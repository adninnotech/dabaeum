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
            session.updatedAt()
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
            row.updatedAt()
        );
    }
}
