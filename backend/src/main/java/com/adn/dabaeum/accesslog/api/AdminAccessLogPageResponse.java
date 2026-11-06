package com.adn.dabaeum.accesslog.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record AdminAccessLogPageResponse(
    List<AdminAccessLogResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
