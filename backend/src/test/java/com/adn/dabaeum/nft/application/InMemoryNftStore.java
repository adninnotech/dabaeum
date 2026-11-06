package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftSourceRepository;
import com.adn.dabaeum.nft.domain.NftTargetType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 테스트용 메모리 장부와 원천. SQL 과 같은 규칙(대상당 한 줄, 기대 상태일 때만 갱신)을 지킨다. */
final class InMemoryNftStore implements NftIssuanceRepository, NftSourceRepository {

    private final Map<UUID, NftIssuance> rows = new LinkedHashMap<>();
    private final List<NftSource> sources = new ArrayList<>();
    private final Set<UUID> revokedTargets = new HashSet<>();

    void issue(NftSource source) {
        sources.add(source);
    }

    void revokeTarget(UUID targetId) {
        revokedTargets.add(targetId);
    }

    NftIssuance only() {
        if (rows.size() != 1) {
            throw new AssertionError("expected one row but was " + rows.size());
        }
        return rows.values().iterator().next();
    }

    List<NftIssuance> all() {
        return List.copyOf(rows.values());
    }

    void put(NftIssuance issuance) {
        rows.put(issuance.id(), issuance);
    }

    @Override
    public boolean insertIfAbsent(NftIssuance issuance) {
        boolean exists = rows.values().stream().anyMatch(row ->
            row.targetType() == issuance.targetType() && row.targetId().equals(issuance.targetId()));
        if (exists) {
            return false;
        }
        rows.put(issuance.id(), issuance);
        return true;
    }

    @Override
    public Optional<NftIssuance> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<NftIssuance> claimDue(int limit, Instant now, String contractForBadge, String contractForCredential) {
        List<NftIssuance> claimed = new ArrayList<>();
        for (NftIssuance row : rows.values().stream()
            .filter(r -> r.status() == NftIssuanceStatus.PENDING)
            .filter(r -> r.nextAttemptAt() == null || !r.nextAttemptAt().isAfter(now))
            .sorted(Comparator.comparing(NftIssuance::createdAt)).limit(limit).toList()) {
            NftIssuance next = row.submitting(
                row.targetType() == NftTargetType.BADGE ? contractForBadge : contractForCredential, now);
            rows.put(row.id(), next);
            claimed.add(next);
        }
        return claimed;
    }

    @Override
    public List<NftIssuance> findSubmitted(int limit) {
        return byStatus(NftIssuanceStatus.SUBMITTED, limit);
    }

    @Override
    public List<NftIssuance> findStaleSubmitting(Instant before, int limit) {
        return rows.values().stream().filter(r -> r.status() == NftIssuanceStatus.SUBMITTING)
            .filter(r -> !r.updatedAt().isAfter(before)).limit(limit).toList();
    }

    @Override
    public List<NftIssuance> findWaitingDomain(int limit) {
        return byStatus(NftIssuanceStatus.WAITING_DOMAIN, limit);
    }

    @Override
    public boolean update(NftIssuanceStatus expected, NftIssuance next) {
        NftIssuance current = rows.get(next.id());
        if (current == null || current.status() != expected) {
            return false;
        }
        rows.put(next.id(), new NftIssuance(next.id(), next.targetType(), next.targetId(), next.status(),
            next.contractAddress(), next.metadataUri(), next.metadataJson(), next.metadataHash(), next.factHash(),
            next.tokenIndex(), next.blockHeight(), next.confirmedAt(), next.retryCount(), next.nextAttemptAt(),
            next.errorCode(), current.revokedAt(), next.createdAt(), next.updatedAt()));
        return true;
    }

    @Override
    public int markRevokedTargets(Instant now) {
        int count = 0;
        for (NftIssuance row : List.copyOf(rows.values())) {
            if (row.revokedAt() == null && revokedTargets.contains(row.targetId())) {
                rows.put(row.id(), new NftIssuance(row.id(), row.targetType(), row.targetId(), row.status(),
                    row.contractAddress(), row.metadataUri(), row.metadataJson(), row.metadataHash(),
                    row.factHash(), row.tokenIndex(), row.blockHeight(), row.confirmedAt(), row.retryCount(),
                    row.nextAttemptAt(), row.errorCode(), now, row.createdAt(), row.updatedAt()));
                count++;
            }
        }
        return count;
    }

    @Override
    public int cancelRevokedBeforeSubmit(Instant now) {
        int count = 0;
        for (NftIssuance row : List.copyOf(rows.values())) {
            if (row.revoked() && (row.status() == NftIssuanceStatus.PENDING
                || row.status() == NftIssuanceStatus.WAITING_DOMAIN)) {
                rows.put(row.id(), row.cancelled(now));
                count++;
            }
        }
        return count;
    }

    @Override
    public List<NftSource> findUnrecorded(NftTargetType type, Instant since, int limit) {
        return sources.stream().filter(s -> s.targetType() == type)
            .filter(s -> !s.issuedAt().isBefore(since))
            .filter(s -> !revokedTargets.contains(s.targetId()))
            .filter(s -> rows.values().stream().noneMatch(r -> r.targetId().equals(s.targetId())))
            .limit(limit).toList();
    }

    @Override
    public Optional<NftSource> find(NftTargetType type, UUID targetId) {
        return sources.stream().filter(s -> s.targetType() == type && s.targetId().equals(targetId)).findFirst();
    }

    private List<NftIssuance> byStatus(NftIssuanceStatus status, int limit) {
        return rows.values().stream().filter(r -> r.status() == status)
            .sorted(Comparator.comparing(NftIssuance::updatedAt)).limit(limit).toList();
    }

    @Override
    public boolean touch(UUID id, NftIssuanceStatus expected, Instant now) {
        NftIssuance current = rows.get(id);
        if (current == null || current.status() != expected) {
            return false;
        }
        rows.put(id, new NftIssuance(current.id(), current.targetType(), current.targetId(), current.status(),
            current.contractAddress(), current.metadataUri(), current.metadataJson(), current.metadataHash(),
            current.factHash(), current.tokenIndex(), current.blockHeight(), current.confirmedAt(),
            current.retryCount(), current.nextAttemptAt(), current.errorCode(), current.revokedAt(),
            current.createdAt(), now));
        return true;
    }
}
