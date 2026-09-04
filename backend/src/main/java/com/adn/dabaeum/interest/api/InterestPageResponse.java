package com.adn.dabaeum.interest.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record InterestPageResponse(
    List<InterestResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
