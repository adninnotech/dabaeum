package com.adn.dabaeum.user.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record UserPageResponse(
    List<UserResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
