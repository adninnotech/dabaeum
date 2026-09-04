package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record LearningBadgePageResponse(
    List<LearningBadgeResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
