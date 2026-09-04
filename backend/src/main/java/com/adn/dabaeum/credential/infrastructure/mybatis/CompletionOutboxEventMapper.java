package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompletionOutboxEventMapper {

    List<CompletionOutboxEventRow> selectPendingForUpdate(
        @Param("limit") int limit,
        @Param("now") Instant now
    );

    int markPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt);

    int reschedule(
        @Param("eventId") UUID eventId,
        @Param("retryCount") int retryCount,
        @Param("nextRetryAt") Instant nextRetryAt,
        @Param("failureCode") String failureCode
    );

    int markFailed(
        @Param("eventId") UUID eventId,
        @Param("retryCount") int retryCount,
        @Param("failureCode") String failureCode
    );
}
