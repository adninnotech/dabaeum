package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.inquiry.domain.InquiryView;
import java.util.List;

public record InquiryViewPage(
    List<InquiryView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
