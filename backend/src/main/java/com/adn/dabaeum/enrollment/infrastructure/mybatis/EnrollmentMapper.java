package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EnrollmentMapper {

    int insert(EnrollmentRow row);

    EnrollmentRow selectById(@Param("id") UUID id);

    EnrollmentRow selectByIdForUpdate(@Param("id") UUID id);

    /** UUID 는 MyBatis 기본 TypeHandler 가 없어 text 로 받는다. 변환은 리포지토리가 한다. */
    List<String> selectInstitutionIdsByUserId(@Param("userId") UUID userId);

    EnrollmentRow selectActiveByCourseIdAndUserId(
        @Param("courseId") UUID courseId,
        @Param("userId") UUID userId
    );

    List<EnrollmentRow> selectPage(@Param("criteria") EnrollmentPageCriteria criteria);

    long countByCourseId(@Param("courseId") UUID courseId);

    long countApprovedByCourseId(@Param("courseId") UUID courseId);

    int updateState(
        @Param("row") EnrollmentRow row,
        @Param("expectedStatus") String expectedStatus
    );
}
