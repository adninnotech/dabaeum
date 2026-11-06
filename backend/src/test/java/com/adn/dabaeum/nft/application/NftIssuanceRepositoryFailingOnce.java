package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 처음 한 번의 SUBMITTED 기록만 DB 오류로 실패시킨다. */
final class NftIssuanceRepositoryFailingOnce implements NftIssuanceRepository {

    private final NftIssuanceRepository delegate;
    private boolean failed;

    NftIssuanceRepositoryFailingOnce(NftIssuanceRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean update(NftIssuanceStatus expected, NftIssuance next) {
        if (!failed && next.status() == NftIssuanceStatus.SUBMITTED) {
            failed = true;
            throw new IllegalStateException("simulated database failure");
        }
        return delegate.update(expected, next);
    }

    @Override
    public boolean insertIfAbsent(NftIssuance issuance) {
        return delegate.insertIfAbsent(issuance);
    }

    @Override
    public Optional<NftIssuance> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public List<NftIssuance> claimDue(int limit, Instant now, String badge, String credential) {
        return delegate.claimDue(limit, now, badge, credential);
    }

    @Override
    public List<NftIssuance> findSubmitted(int limit) {
        return delegate.findSubmitted(limit);
    }

    @Override
    public List<NftIssuance> findStaleSubmitting(Instant before, int limit) {
        return delegate.findStaleSubmitting(before, limit);
    }

    @Override
    public List<NftIssuance> findWaitingDomain(int limit) {
        return delegate.findWaitingDomain(limit);
    }

    @Override
    public boolean touch(UUID id, NftIssuanceStatus expected, Instant now) {
        return delegate.touch(id, expected, now);
    }

    @Override
    public int markRevokedTargets(Instant now) {
        return delegate.markRevokedTargets(now);
    }

    @Override
    public int cancelRevokedBeforeSubmit(Instant now) {
        return delegate.cancelRevokedBeforeSubmit(now);
    }
}
