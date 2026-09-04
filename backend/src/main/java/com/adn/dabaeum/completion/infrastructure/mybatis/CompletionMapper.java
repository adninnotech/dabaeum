package com.adn.dabaeum.completion.infrastructure.mybatis;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompletionMapper {

    int insert(CompletionRow row);

    CompletionRow selectById(@Param("completionId") UUID completionId);

    CompletionRow selectByIdForUpdate(@Param("completionId") UUID completionId);

    CompletionRow selectByEnrollmentId(@Param("enrollmentId") UUID enrollmentId);

    CompletionRow selectByEnrollmentIdForUpdate(@Param("enrollmentId") UUID enrollmentId);

    int updateEvaluation(
        @Param("row") CompletionRow row,
        @Param("expectedStatus") String expectedStatus
    );

    int confirm(
        @Param("row") CompletionRow row,
        @Param("expectedStatus") String expectedStatus
    );

    int revertConfirmation(
        @Param("row") CompletionRow row,
        @Param("expectedStatus") String expectedStatus
    );
}
