package com.adn.dabaeum.attendance.infrastructure.mybatis;

import com.adn.dabaeum.attendance.domain.AttendancePageCriteria;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AttendanceMapper {

    int insert(AttendanceRow row);

    AttendanceRow selectById(@Param("id") UUID id);

    AttendanceRow selectByIdForUpdate(@Param("id") UUID id);

    AttendanceRow selectBySessionAndEnrollment(
        @Param("sessionId") UUID sessionId,
        @Param("enrollmentId") UUID enrollmentId
    );

    List<AttendanceRow> selectBySession(@Param("criteria") AttendancePageCriteria criteria);

    long countBySession(@Param("sessionId") UUID sessionId);

    List<AttendanceRow> selectByEnrollment(@Param("enrollmentId") UUID enrollmentId);

    int updateStatus(
        @Param("id") UUID id,
        @Param("expectedStatus") String expectedStatus,
        @Param("nextStatus") String nextStatus,
        @Param("updatedAt") Instant updatedAt
    );
}
