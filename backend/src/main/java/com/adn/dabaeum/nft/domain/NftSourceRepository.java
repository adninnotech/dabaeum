package com.adn.dabaeum.nft.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 발급됐지만 아직 장부에 없는 수료증·배지와, 메타데이터 원천 조회. */
public interface NftSourceRepository {

    /** {@code since} 이후 발급된 유효한 대상 중 장부에 없는 것. */
    List<NftSource> findUnrecorded(NftTargetType type, Instant since, int limit);

    Optional<NftSource> find(NftTargetType type, UUID targetId);
}
