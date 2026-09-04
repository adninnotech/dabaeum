package com.adn.dabaeum.inquiry.api;

import java.util.UUID;

public record InquiryCreateRequest(
    String title,
    String content,
    UUID courseId
) {
}
