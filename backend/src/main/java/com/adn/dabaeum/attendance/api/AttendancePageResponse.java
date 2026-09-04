package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record AttendancePageResponse(
    List<AttendanceResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
