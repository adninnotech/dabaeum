package com.adn.dabaeum.banner.application;

import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.List;
import java.util.UUID;

public interface BannerService {

    /** {@code visibleOnly} 면 지금 보이는 배너만, 아니면 비공개까지 전부 돌려준다. */
    List<Banner> list(boolean visibleOnly);

    Banner create(BannerCommand command, AuthenticatedUserContext actor);

    Banner update(UUID bannerId, BannerCommand command, AuthenticatedUserContext actor);

    void delete(UUID bannerId, AuthenticatedUserContext actor);
}
