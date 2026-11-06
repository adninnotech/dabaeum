package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 키 버전 이력 (설계서 §6.1, §11.2). 공개키 JWK 는 kid 없이 보관하고 kid 는 조회 시 §6.2 규칙으로
 * 조립한다. COMPROMISED 는 어떤 전이로도 바뀌지 않는다.
 */
public record InstitutionSignKeyVersion(
    UUID id,
    UUID signKeyId,
    UUID institutionId,
    int keyVersion,
    String kidFragment,
    String publicKeyJwk,
    Source source,
    SignKeyVersionStatus status,
    Instant openbaoCreatedAt,
    Instant activatedAt,
    Instant retiredAt,
    Instant compromisedAt,
    UUID createdBy,
    Instant createdAt
) {

    public enum Source {
        OPENBAO,
        LOCAL_FILE
    }

    public InstitutionSignKeyVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(signKeyId, "signKeyId");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(kidFragment, "kidFragment");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (keyVersion < 0) {
            throw new IllegalArgumentException("keyVersion must not be negative");
        }
        if (publicKeyJwk == null || publicKeyJwk.isBlank()) {
            throw new IllegalArgumentException("publicKeyJwk is required");
        }
        if (publicKeyJwk.contains("\"kid\"")) {
            throw new IllegalArgumentException("publicKeyJwk must not carry kid");
        }
        if (status == SignKeyVersionStatus.RETIRED && retiredAt == null) {
            throw new IllegalStateException("retiredAt is required");
        }
        if (status == SignKeyVersionStatus.COMPROMISED && compromisedAt == null) {
            throw new IllegalStateException("compromisedAt is required");
        }
    }

    /** Transit 재조회로 확인한 새 버전 (§5.4, §6.2 T2). */
    public static InstitutionSignKeyVersion active(
        UUID id, UUID signKeyId, UUID institutionId, SignKeyPurpose purpose, int generation,
        int keyVersion, String publicKeyJwk, Instant openbaoCreatedAt, UUID createdBy, Instant now
    ) {
        return new InstitutionSignKeyVersion(id, signKeyId, institutionId, keyVersion,
            SignKeyNaming.kidFragment(purpose, generation, keyVersion), publicKeyJwk,
            Source.OPENBAO, SignKeyVersionStatus.ACTIVE, openbaoCreatedAt, now, null, null,
            createdBy, now);
    }

    /** 전환 이전 플랫폼 키 (§9.2 표). 수료증 키 마스터 아래에 버전 0 으로 둔다. */
    public static InstitutionSignKeyVersion legacy(
        UUID id, UUID signKeyId, UUID institutionId, String publicKeyJwk, Instant now
    ) {
        return new InstitutionSignKeyVersion(id, signKeyId, institutionId,
            SignKeyNaming.LEGACY_KEY_VERSION, SignKeyNaming.LEGACY_KID_FRAGMENT, publicKeyJwk,
            Source.LOCAL_FILE, SignKeyVersionStatus.RETIRED, null, now, now, null, null, now);
    }

    /** 정상 Rotation 으로 사용 종료. 검증에는 계속 쓴다. */
    public InstitutionSignKeyVersion retire(Instant now) {
        if (status != SignKeyVersionStatus.ACTIVE) {
            throw new IllegalStateException("only an active version can be retired");
        }
        return new InstitutionSignKeyVersion(id, signKeyId, institutionId, keyVersion, kidFragment,
            publicKeyJwk, source, SignKeyVersionStatus.RETIRED, openbaoCreatedAt, activatedAt,
            Objects.requireNonNull(now, "now"), compromisedAt, createdBy, createdAt);
    }

    /** 유출 선언 즉시 동작 (§6.5). 승인된 사고 시각을 기록한다. */
    public InstitutionSignKeyVersion compromise(Instant approvedCompromisedAt) {
        if (status == SignKeyVersionStatus.COMPROMISED) {
            throw new IllegalStateException("a compromised version stays compromised");
        }
        return new InstitutionSignKeyVersion(id, signKeyId, institutionId, keyVersion, kidFragment,
            publicKeyJwk, source, SignKeyVersionStatus.COMPROMISED, openbaoCreatedAt, activatedAt,
            retiredAt, Objects.requireNonNull(approvedCompromisedAt, "approvedCompromisedAt"),
            createdBy, createdAt);
    }

    /** 발급자 문서에 공개하는 버전. COMPROMISED 만 제외한다 (§9.2). */
    public boolean isPublished() {
        return status != SignKeyVersionStatus.COMPROMISED;
    }
}
