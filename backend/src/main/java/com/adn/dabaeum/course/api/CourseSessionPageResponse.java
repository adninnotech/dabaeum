package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record CourseSessionPageResponse(
    List<CourseSessionResponse> data, PageMeta page, ApiMeta meta
) {
}
