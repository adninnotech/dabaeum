package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record InstitutionEnrollmentPageResponse(
    List<InstitutionEnrollmentResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
