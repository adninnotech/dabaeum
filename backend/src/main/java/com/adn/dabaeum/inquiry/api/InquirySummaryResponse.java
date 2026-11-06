package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.enrollment.api.CourseCardFields;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.UUID;

public record InquirySummaryResponse(
    UUID id,
    UUID courseId,
    String title,
    InquiryStatus status,
    InquiryType inquiryType,
    Instant createdAt,
    String courseTitle,
    /** 관리자에게는 실명, 그 외에는 마스킹된 이름. */
    String userName,
    /** 관리자에게만 노출. 그 외에는 null. */
    String email,
    /** 관리자에게만 노출. 그 외에는 null. */
    String phone,
    UUID profileImageId,
    @JsonUnwrapped CourseCardFields card
) {
}
