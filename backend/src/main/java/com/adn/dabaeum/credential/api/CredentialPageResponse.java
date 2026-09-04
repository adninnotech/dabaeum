package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record CredentialPageResponse(
    List<CredentialDetailResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
