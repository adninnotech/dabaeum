package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record InstitutionJoinApplicationPageResponse(
    List<InstitutionJoinApplicationResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
