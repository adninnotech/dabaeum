package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SigningKeyRequestQueueMapper {

    List<SigningKeyRequestRow> selectPendingForUpdate(
        @Param("limit") int limit,
        @Param("now") Instant now
    );

    int lease(@Param("eventId") UUID eventId, @Param("leaseUntil") Instant leaseUntil);

    int markDone(
        @Param("eventId") UUID eventId,
        @Param("leaseUntil") Instant leaseUntil,
        @Param("doneAt") Instant doneAt
    );

    int reschedule(
        @Param("eventId") UUID eventId,
        @Param("leaseUntil") Instant leaseUntil,
        @Param("retryCount") int retryCount,
        @Param("nextRetryAt") Instant nextRetryAt,
        @Param("failureCode") String failureCode
    );

    int markFailed(
        @Param("eventId") UUID eventId,
        @Param("leaseUntil") Instant leaseUntil,
        @Param("retryCount") int retryCount,
        @Param("failureCode") String failureCode
    );
}
