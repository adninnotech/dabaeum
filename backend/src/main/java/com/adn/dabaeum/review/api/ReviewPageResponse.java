package com.adn.dabaeum.review.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record ReviewPageResponse(
    List<ReviewResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
