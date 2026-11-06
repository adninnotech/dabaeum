package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftSourceRepository;
import com.adn.dabaeum.nft.domain.NftTargetType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 발급된 수료증·배지를 장부에 대기로 올리고, 폐기된 대상을 표시한다.
 *
 * <p>수료증·배지 발급 코드에 끼어들지 않고 주기적으로 훑는다. 수료증은 대구체인 Storage 원장 기록이
 * 확정된 뒤에야 ISSUED 가 되므로 NFT 는 항상 원장 확정 뒤에 나간다. {@code startAt} 이전 발급분은
 * 옮기지 않는다(설계서 6절).
 */
public final class NftIssuanceEnqueuer {

    private final NftIssuanceRepository issuances;
    private final NftSourceRepository sources;
    private final NftMetadataFactory metadata;
    private final Instant startAt;
    private final String metadataBaseUrl;
    private final Supplier<UUID> ids;

    public NftIssuanceEnqueuer(NftIssuanceRepository issuances, NftSourceRepository sources,
        NftMetadataFactory metadata, Instant startAt, String metadataBaseUrl, Supplier<UUID> ids) {
        this.issuances = Objects.requireNonNull(issuances, "issuances");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.startAt = Objects.requireNonNull(startAt, "startAt");
        this.metadataBaseUrl = metadataBaseUrl == null || metadataBaseUrl.isBlank() ? null : metadataBaseUrl.trim();
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public void enqueue(Instant now, int limit) {
        for (NftTargetType type : NftTargetType.values()) {
            for (NftSource source : sources.findUnrecorded(type, startAt, limit)) {
                UUID id = ids.get();
                issuances.insertIfAbsent(!metadata.canBuild(metadataBaseUrl)
                    ? NftIssuance.waitingDomain(id, type, source.targetId(), now)
                    : NftIssuance.pending(id, type, source.targetId(), metadata.build(source, id, metadataBaseUrl), now));
            }
        }
        if (metadata.canBuild(metadataBaseUrl)) {
            for (NftIssuance waiting : issuances.findWaitingDomain(limit)) {
                Optional<NftSource> source = sources.find(waiting.targetType(), waiting.targetId());
                if (source.isPresent()) {
                    issuances.update(NftIssuanceStatus.WAITING_DOMAIN,
                        waiting.withMetadata(metadata.build(source.get(), waiting.id(), metadataBaseUrl), now));
                } else {
                    issuances.touch(waiting.id(), NftIssuanceStatus.WAITING_DOMAIN, now);
                }
            }
        }
        issuances.markRevokedTargets(now);
        issuances.cancelRevokedBeforeSubmit(now);
    }
}
