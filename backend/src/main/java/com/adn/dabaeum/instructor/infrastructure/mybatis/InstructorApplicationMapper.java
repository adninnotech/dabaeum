package com.adn.dabaeum.instructor.infrastructure.mybatis;

import com.adn.dabaeum.instructor.domain.InstructorApplicationPageCriteria;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstructorApplicationMapper {

    int insert(InstructorApplicationRow row);

    InstructorApplicationRow selectById(@Param("id") UUID id);

    InstructorApplicationRow selectByIdForUpdate(@Param("id") UUID id);

    InstructorApplicationRow selectPending(
        @Param("userId") UUID userId,
        @Param("institutionId") UUID institutionId
    );

    List<InstructorApplicationRow> selectPage(
        @Param("criteria") InstructorApplicationPageCriteria criteria
    );

    long count(@Param("criteria") InstructorApplicationPageCriteria criteria);

    int updateReview(
        @Param("row") InstructorApplicationRow row,
        @Param("expectedStatus") InstructorApplicationStatus expectedStatus
    );
}
