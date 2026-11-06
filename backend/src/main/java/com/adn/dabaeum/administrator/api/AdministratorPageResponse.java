package com.adn.dabaeum.administrator.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record AdministratorPageResponse(List<AdministratorResponse> data, PageMeta page, ApiMeta meta) {
}
