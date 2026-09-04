package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiMeta;
import java.util.List;

public record CommonCodeListResponse(
    List<CommonCodeResponse> data,
    ApiMeta meta
) {
}
