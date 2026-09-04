package com.adn.dabaeum.inquiry.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository {

    void save(Inquiry inquiry);

    Optional<Inquiry> findById(UUID id);

    boolean updateAnswer(Inquiry inquiry);

    List<InquiryView> findByUserId(
        UUID userId, InquiryStatus status, int limit, int offset);

    long countByUserId(UUID userId, InquiryStatus status);

    List<InquiryView> findByInstructorUserId(
        UUID instructorUserId, InquiryStatus status, UUID courseId, int limit, int offset);

    long countByInstructorUserId(
        UUID instructorUserId, InquiryStatus status, UUID courseId);

    List<InquiryView> findByInstitutionId(
        UUID institutionId, InquiryStatus status, UUID courseId, int limit, int offset);

    long countByInstitutionId(UUID institutionId, InquiryStatus status, UUID courseId);
}
