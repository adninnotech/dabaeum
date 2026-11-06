package com.adn.dabaeum.banner.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BannerRepository {

    void save(Banner banner);

    Optional<Banner> findById(UUID id);

    /**
     * 삭제되지 않은 배너 전부를 {@code sort_order, created_at DESC} 순으로 돌려준다.
     *
     * <p>노출 판정은 {@link Banner#isVisibleAt(Instant)} 한 곳에서만 한다. 배너는 홈 최상단
     * 캐러셀용이라 많아야 수십 장이고 페이징도 없으므로 전량을 읽어 자바에서 거른다.
     */
    List<Banner> findAll();

    boolean update(Banner banner);

    boolean softDelete(UUID id, Instant deletedAt);
}
