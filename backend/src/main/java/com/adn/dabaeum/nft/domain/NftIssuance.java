package com.adn.dabaeum.nft.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 발행 장부 한 줄. 불변이며 상태 변경은 새 객체를 돌려준다.
 *
 * @param metadataJson 체인에 적힌 해시의 원문. 공개 메타데이터 API 는 이 원문을 그대로 내준다
 */
public record NftIssuance(
    UUID id,
    NftTargetType targetType,
    UUID targetId,
    NftIssuanceStatus status,
    String contractAddress,
    String metadataUri,
    String metadataJson,
    String metadataHash,
    String factHash,
    Long tokenIndex,
    Long blockHeight,
    Instant confirmedAt,
    int retryCount,
    Instant nextAttemptAt,
    String errorCode,
    Instant revokedAt,
    Instant createdAt,
    Instant updatedAt
) {

    public NftIssuance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }

    public static NftIssuance waitingDomain(UUID id, NftTargetType type, UUID targetId, Instant now) {
        return new NftIssuance(id, type, targetId, NftIssuanceStatus.WAITING_DOMAIN, null, null, null, null,
            null, null, null, null, 0, null, null, null, now, now);
    }

    public static NftIssuance pending(UUID id, NftTargetType type, UUID targetId, NftMetadata metadata, Instant now) {
        return waitingDomain(id, type, targetId, now).withMetadata(metadata, now);
    }

    public NftIssuance withMetadata(NftMetadata metadata, Instant now) {
        return new NftIssuance(id, targetType, targetId, NftIssuanceStatus.PENDING, contractAddress,
            metadata.uri(), metadata.json(), metadata.hash(), factHash, tokenIndex, blockHeight, confirmedAt,
            retryCount, null, null, revokedAt, createdAt, now);
    }

    public NftIssuance submitting(String contract, Instant now) {
        return next(NftIssuanceStatus.SUBMITTING, contract, factHash, tokenIndex, blockHeight, confirmedAt,
            retryCount, null, null, now);
    }

    public NftIssuance submitted(String fact, Instant now) {
        return next(NftIssuanceStatus.SUBMITTED, contractAddress, fact, tokenIndex, blockHeight, confirmedAt,
            retryCount, null, null, now);
    }

    public NftIssuance minted(long index, Long height, Instant confirmed, Instant now) {
        return next(NftIssuanceStatus.MINTED, contractAddress, factHash, index, height, confirmed,
            retryCount, null, null, now);
    }

    /** 체인에 아무것도 안 생긴 실패. 다시 보낼 시각이 있으면 대기로, 없으면 실패로 둔다. */
    public NftIssuance retryLater(String code, Instant at, Instant now) {
        return next(NftIssuanceStatus.PENDING, contractAddress, null, null, null, null,
            retryCount + 1, at, code, now);
    }

    public NftIssuance failed(String code, Instant now) {
        return next(NftIssuanceStatus.FAILED, contractAddress, factHash, null, null, null,
            retryCount, null, code, now);
    }

    public NftIssuance unknown(String code, Instant now) {
        return next(NftIssuanceStatus.UNKNOWN, contractAddress, factHash, tokenIndex, blockHeight, confirmedAt,
            retryCount, null, code, now);
    }

    public NftIssuance cancelled(Instant now) {
        return next(NftIssuanceStatus.CANCELLED, contractAddress, factHash, tokenIndex, blockHeight, confirmedAt,
            retryCount, null, null, now);
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    private NftIssuance next(NftIssuanceStatus nextStatus, String contract, String fact, Long index, Long height,
        Instant confirmed, int retries, Instant nextAttempt, String code, Instant now) {
        return new NftIssuance(id, targetType, targetId, nextStatus, contract, metadataUri, metadataJson,
            metadataHash, fact, index, height, confirmed, retries, nextAttempt, code, revokedAt, createdAt, now);
    }
}
