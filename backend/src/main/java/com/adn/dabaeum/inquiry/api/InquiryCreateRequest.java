package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.inquiry.domain.InquiryType;
import java.util.UUID;

public record InquiryCreateRequest(
    String title,
    String content,
    UUID courseId,
    /** 생략하면 COURSE(강의 문의)로 등록된다. */
    InquiryType type
) {
}
