package com.adn.dabaeum.nft.infrastructure.mybatis;

import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftSourceRepository;
import com.adn.dabaeum.nft.domain.NftTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NftSourceMyBatisRepository implements NftSourceRepository {

    private final NftSourceMapper mapper;

    public NftSourceMyBatisRepository(NftSourceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<NftSource> findUnrecorded(NftTargetType type, Instant since, int limit) {
        List<NftSourceRow> rows = type == NftTargetType.CREDENTIAL
            ? mapper.selectUnrecordedCredentials(since, limit)
            : mapper.selectUnrecordedBadges(since, limit);
        return rows.stream().map(row -> toSource(type, row)).toList();
    }

    @Override
    public Optional<NftSource> find(NftTargetType type, UUID targetId) {
        NftSourceRow row = type == NftTargetType.CREDENTIAL
            ? mapper.selectCredential(targetId) : mapper.selectBadge(targetId);
        return Optional.ofNullable(row).map(found -> toSource(type, found));
    }

    private static NftSource toSource(NftTargetType type, NftSourceRow row) {
        return new NftSource(type, row.targetId(), row.issuedAt(), row.title(), row.description(),
            row.issuerName(), row.imageFileId(), row.contentHash());
    }
}
