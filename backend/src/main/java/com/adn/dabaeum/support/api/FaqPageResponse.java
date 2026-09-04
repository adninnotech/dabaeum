package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record FaqPageResponse(
    List<FaqResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
