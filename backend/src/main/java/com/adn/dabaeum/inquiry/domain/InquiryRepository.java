package com.adn.dabaeum.inquiry.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository {

    void save(Inquiry inquiry);

    Optional<Inquiry> findById(UUID id);

    boolean updateAnswer(Inquiry inquiry);

    List<InquiryView> findByUserId(
        UUID userId, InquiryStatus status, String keyword, int limit, int offset);

    long countByUserId(UUID userId, InquiryStatus status, String keyword);

    List<InquiryView> findByInstructorUserId(
        UUID instructorUserId, InquiryStatus status, UUID courseId, int limit, int offset);

    long countByInstructorUserId(
        UUID instructorUserId, InquiryStatus status, UUID courseId);

    List<InquiryView> findByInstitutionId(
        UUID institutionId, InquiryStatus status, UUID courseId, int limit, int offset);

    long countByInstitutionId(UUID institutionId, InquiryStatus status, UUID courseId);

    /** C-6·E-2 가 공유하는 강좌별 문의 목록. */
    List<InquiryView> findByCourseId(
        UUID courseId, InquiryType type, int limit, int offset);

    long countByCourseId(UUID courseId, InquiryType type);

    /**
     * B-7 플랫폼 관리자 사이트 전체 문의 목록. findByInstitutionId 와 같은 SELECT·조인을
     * 기관 조건 없이 쓴다. keyword 는 문의 제목·강의명에 매칭한다.
     */
    List<InquiryView> findForAdmin(
        InquiryStatus status, InquiryType inquiryType,
        com.adn.dabaeum.course.domain.CourseEducationType educationType,
        UUID institutionId, UUID courseId, String keyword, int limit, int offset);

    long countForAdmin(
        InquiryStatus status, InquiryType inquiryType,
        com.adn.dabaeum.course.domain.CourseEducationType educationType,
        UUID institutionId, UUID courseId, String keyword);
}
