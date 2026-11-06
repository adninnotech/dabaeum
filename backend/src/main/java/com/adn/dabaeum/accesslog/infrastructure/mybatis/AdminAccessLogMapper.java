package com.adn.dabaeum.accesslog.infrastructure.mybatis;

import com.adn.dabaeum.accesslog.domain.AdminAccessLogCriteria;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccessLogMapper {

    int insert(AdminAccessLogRow row);

    List<AdminAccessLogEntryRow> search(@Param("criteria") AdminAccessLogCriteria criteria);

    long count(@Param("criteria") AdminAccessLogCriteria criteria);

    int deleteCreatedBefore(@Param("threshold") Instant threshold);
}
