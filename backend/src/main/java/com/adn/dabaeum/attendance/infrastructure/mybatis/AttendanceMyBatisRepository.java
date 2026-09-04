package com.adn.dabaeum.attendance.infrastructure.mybatis;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendancePageCriteria;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class AttendanceMyBatisRepository implements AttendanceRepository {

    private final AttendanceMapper mapper;

    public AttendanceMyBatisRepository(AttendanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Attendance attendance) {
        mapper.insert(toRow(attendance));
    }

    @Override
    public Optional<Attendance> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Attendance> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id)).map(this::toDomain);
    }

    @Override
    public Optional<Attendance> findBySessionAndEnrollment(UUID sessionId, UUID enrollmentId) {
        return Optional.ofNullable(mapper.selectBySessionAndEnrollment(sessionId, enrollmentId))
            .map(this::toDomain);
    }

    @Override
    public List<Attendance> findBySession(UUID sessionId, AttendancePageCriteria criteria) {
        if (!sessionId.equals(criteria.sessionId())) {
            throw new IllegalArgumentException("criteria sessionId must match query sessionId");
        }
        return mapper.selectBySession(criteria).stream().map(this::toDomain).toList();
    }

    @Override
    public long countBySession(UUID sessionId) {
        return mapper.countBySession(sessionId);
    }

    @Override
    public List<Attendance> findByEnrollment(UUID enrollmentId) {
        return mapper.selectByEnrollment(enrollmentId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean updateStatus(
        UUID id,
        AttendanceStatus expected,
        AttendanceStatus next,
        Instant updatedAt
    ) {
        return mapper.updateStatus(id, expected.name(), next.name(), updatedAt) == 1;
    }

    private AttendanceRow toRow(Attendance attendance) {
        return new AttendanceRow(
            attendance.id(),
            attendance.courseId(),
            attendance.sessionId(),
            attendance.enrollmentId(),
            attendance.qrTokenId(),
            attendance.attendanceMethod().name(),
            attendance.status().name(),
            attendance.checkedAt(),
            attendance.source().name(),
            attendance.createdBy(),
            attendance.createdAt(),
            attendance.updatedAt()
        );
    }

    private Attendance toDomain(AttendanceRow row) {
        return new Attendance(
            row.id(),
            row.courseId(),
            row.sessionId(),
            row.enrollmentId(),
            row.qrTokenId(),
            AttendanceMethod.valueOf(row.attendanceMethod()),
            AttendanceStatus.valueOf(row.status()),
            row.checkedAt(),
            AttendanceSource.valueOf(row.source()),
            row.createdBy(),
            row.createdAt(),
            row.updatedAt()
        );
    }
}
