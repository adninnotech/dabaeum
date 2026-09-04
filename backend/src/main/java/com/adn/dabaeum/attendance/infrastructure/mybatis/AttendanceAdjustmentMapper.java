package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AttendanceAdjustmentMapper {

    int insert(AttendanceAdjustmentRow row);

    List<AttendanceAdjustmentRow> selectByAttendanceId(
        @Param("attendanceId") UUID attendanceId
    );
}
