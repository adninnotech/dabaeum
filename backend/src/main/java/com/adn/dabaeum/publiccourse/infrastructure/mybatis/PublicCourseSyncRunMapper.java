package com.adn.dabaeum.publiccourse.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 수집 실행 이력 매퍼. 기관별 결과는 JSON 문자열로 주고받는다. */
@Mapper
public interface PublicCourseSyncRunMapper {

    int insert(RunRow row);

    int update(RunRow row);

    RunRow selectById(@Param("id") UUID id);

    List<RunRow> selectPage(@Param("limit") int limit, @Param("offset") long offset);

    long count();

    int closeStaleRunning(@Param("startedBefore") Instant startedBefore, @Param("at") Instant at);

    record RunRow(
        UUID id,
        String triggerType,
        UUID requestedBy,
        String status,
        Instant startedAt,
        Instant finishedAt,
        int fetched,
        int inserted,
        int updated,
        int unchanged,
        int deactivated,
        int skipped,
        int parseFailures,
        int keyCollisions,
        String institutionResults,
        String errorCode
    ) {
    }
}
