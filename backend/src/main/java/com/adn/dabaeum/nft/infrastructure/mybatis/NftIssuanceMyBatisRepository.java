package com.adn.dabaeum.nft.infrastructure.mybatis;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NftIssuanceMyBatisRepository implements NftIssuanceRepository {

    private final NftIssuanceMapper mapper;

    public NftIssuanceMyBatisRepository(NftIssuanceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public boolean insertIfAbsent(NftIssuance issuance) {
        return mapper.insertIfAbsent(issuance) == 1;
    }

    @Override
    public Optional<NftIssuance> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<NftIssuance> claimDue(int limit, Instant now, String contractForBadge, String contractForCredential) {
        return mapper.claimDue(limit, now, contractForBadge, contractForCredential);
    }

    @Override
    public List<NftIssuance> findSubmitted(int limit) {
        return mapper.selectByStatus(NftIssuanceStatus.SUBMITTED.name(), limit);
    }

    @Override
    public List<NftIssuance> findStaleSubmitting(Instant before, int limit) {
        return mapper.selectStaleSubmitting(before, limit);
    }

    @Override
    public List<NftIssuance> findWaitingDomain(int limit) {
        return mapper.selectByStatus(NftIssuanceStatus.WAITING_DOMAIN.name(), limit);
    }

    @Override
    public boolean update(NftIssuanceStatus expected, NftIssuance next) {
        return mapper.updateFrom(expected.name(), next) == 1;
    }

    @Override
    public boolean touch(UUID id, NftIssuanceStatus expected, Instant now) {
        return mapper.touch(id, expected.name(), now) == 1;
    }

    @Override
    public int markRevokedTargets(Instant now) {
        return mapper.markRevokedTargets(now);
    }

    @Override
    public int cancelRevokedBeforeSubmit(Instant now) {
        return mapper.cancelRevokedBeforeSubmit(now);
    }
}
