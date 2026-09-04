package com.adn.dabaeum.attendance.infrastructure.mybatis;

import com.adn.dabaeum.attendance.domain.AttendanceAdjustment;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentRepository;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class AttendanceAdjustmentMyBatisRepository
    implements AttendanceAdjustmentRepository {

    private final AttendanceAdjustmentMapper mapper;

    public AttendanceAdjustmentMyBatisRepository(AttendanceAdjustmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AttendanceAdjustment adjustment) {
        mapper.insert(new AttendanceAdjustmentRow(
            adjustment.id(), adjustment.attendanceRecordId(),
            adjustment.beforeStatus().name(), adjustment.afterStatus().name(),
            adjustment.reason(), adjustment.adjustedBy(), adjustment.adjustedAt(),
            adjustment.createdAt()));
    }

    @Override
    public List<AttendanceAdjustment> findByAttendanceId(UUID attendanceId) {
        return mapper.selectByAttendanceId(attendanceId).stream()
            .map(row -> new AttendanceAdjustment(
                row.id(), row.attendanceRecordId(),
                AttendanceStatus.valueOf(row.beforeStatus()),
                AttendanceStatus.valueOf(row.afterStatus()), row.reason(), row.adjustedBy(),
                row.adjustedAt(), row.createdAt()))
            .toList();
    }
}
