package com.adn.dabaeum.instructor.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record InstructorApplicationPageResponse(
    List<InstructorApplicationResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
