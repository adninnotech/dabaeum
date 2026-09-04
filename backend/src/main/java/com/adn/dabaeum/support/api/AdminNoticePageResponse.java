package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record AdminNoticePageResponse(
    List<NoticeResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
