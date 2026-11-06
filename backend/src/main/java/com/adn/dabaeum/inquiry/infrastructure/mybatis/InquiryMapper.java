package com.adn.dabaeum.inquiry.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InquiryMapper {

    int insert(InquiryRow row);

    InquiryRow selectById(@Param("id") UUID id);

    int updateAnswer(InquiryRow row);

    List<InquiryViewRow> selectByUserId(
        @Param("userId") UUID userId,
        @Param("status") String status,
        @Param("keyword") String keyword,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByUserId(
        @Param("userId") UUID userId,
        @Param("status") String status,
        @Param("keyword") String keyword
    );

    List<InquiryViewRow> selectByInstructorUserId(
        @Param("instructorUserId") UUID instructorUserId,
        @Param("status") String status,
        @Param("courseId") UUID courseId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByInstructorUserId(
        @Param("instructorUserId") UUID instructorUserId,
        @Param("status") String status,
        @Param("courseId") UUID courseId
    );

    List<InquiryViewRow> selectByInstitutionId(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status,
        @Param("courseId") UUID courseId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByInstitutionId(
        @Param("institutionId") UUID institutionId,
        @Param("status") String status,
        @Param("courseId") UUID courseId
    );

    List<InquiryViewRow> selectByCourseId(
        @Param("courseId") UUID courseId,
        @Param("type") String type,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByCourseId(
        @Param("courseId") UUID courseId,
        @Param("type") String type
    );

    List<InquiryViewRow> selectForAdmin(
        @Param("status") String status,
        @Param("inquiryType") String inquiryType,
        @Param("educationType") String educationType,
        @Param("institutionId") UUID institutionId,
        @Param("courseId") UUID courseId,
        @Param("keyword") String keyword,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countForAdmin(
        @Param("status") String status,
        @Param("inquiryType") String inquiryType,
        @Param("educationType") String educationType,
        @Param("institutionId") UUID institutionId,
        @Param("courseId") UUID courseId,
        @Param("keyword") String keyword
    );
}
