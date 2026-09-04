package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record InquiryPageResponse(
    List<InquirySummaryResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
