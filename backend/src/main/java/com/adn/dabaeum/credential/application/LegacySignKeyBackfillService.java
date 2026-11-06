package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.LegacySignKeyBackfillRepository;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전환 이전 발급분(`#development-1`)이 있는 기관마다 수료증 키 마스터와 버전 0 행을 만들고 기존
 * 수료증·Status List 행을 연결한다 (설계서 §9.2 표, §11.3 2~3번). 마이그레이션이 아니라
 * 서비스인 이유는 플랫폼 공개키 JWK 를 런타임 파일에서만 얻을 수 있기 때문이다.
 *
 * <p>멱등이다. 1세대 수료증 마스터를 상태와 무관하게 재사용하며, 세대가 바뀌어도 버전 0 행은
 * 1세대에 붙는다. 버전 0 행이 있으면 건너뛰며, 연결 UPDATE 는 `sign_key_id IS NULL` 인 행만
 * 고친다. 계획 7 이 서버에서 한 번 실행한 뒤 V23 CHECK 를 건다.
 */
@Service
public class LegacySignKeyBackfillService {

    public record Result(
        UUID institutionId,
        boolean masterCreated,
        boolean versionCreated,
        int credentialsLinked,
        int statusListsLinked
    ) {
    }

    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final LegacySignKeyBackfillRepository backfill;
    private final CredentialPublicJwkFactory jwkFactory;
    private final VcProperties vcProperties;
    private final Supplier<PublicKey> platformPublicKey;

    /** 생성자가 둘이라 Spring 이 고를 수 있게 표시한다. 아래 package-private 생성자는 테스트용. */
    @Autowired
    public LegacySignKeyBackfillService(
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        LegacySignKeyBackfillRepository backfill,
        CredentialPublicJwkFactory jwkFactory,
        VcProperties vcProperties,
        ObjectProvider<PublicKey> platformPublicKey
    ) {
        this(keys, versions, backfill, jwkFactory, vcProperties, platformPublicKey::getIfAvailable);
    }

    LegacySignKeyBackfillService(
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        LegacySignKeyBackfillRepository backfill,
        CredentialPublicJwkFactory jwkFactory,
        VcProperties vcProperties,
        Supplier<PublicKey> platformPublicKey
    ) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.backfill = Objects.requireNonNull(backfill, "backfill");
        this.jwkFactory = Objects.requireNonNull(jwkFactory, "jwkFactory");
        this.vcProperties = Objects.requireNonNull(vcProperties, "vcProperties");
        this.platformPublicKey = Objects.requireNonNull(platformPublicKey, "platformPublicKey");
    }

    @Transactional
    public List<Result> backfill(Instant now) {
        Objects.requireNonNull(now, "now");
        // 버전 0 행의 kid_fragment 는 이미 발급된 JWS 헤더의 kid 조각과 같아야 한다. 배포마다
        // dabaeum.vc.key-version 을 바꿀 수 있으므로, 백필이 쓰는 상수와 다르면 아무것도 고치지
        // 않고 거부한다.
        if (!SignKeyNaming.LEGACY_KID_FRAGMENT.equals(vcProperties.keyVersion())) {
            throw new IllegalStateException(
                "credential-proof legacy kid fragment mismatch: dabaeum.vc.key-version="
                    + vcProperties.keyVersion() + " but backfill writes " + SignKeyNaming.LEGACY_KID_FRAGMENT);
        }
        List<UUID> institutions = backfill.findInstitutionsWithUnlinkedRows();
        if (institutions.isEmpty()) {
            return List.of();
        }
        String legacyJwk = legacyPublicKeyJwk();
        List<Result> results = new ArrayList<>();
        for (UUID institutionId : institutions) {
            results.add(backfillInstitution(institutionId, legacyJwk, now));
        }
        return results;
    }

    private Result backfillInstitution(UUID institutionId, String legacyJwk, Instant now) {
        boolean masterCreated = false;
        InstitutionSignKey master = findGeneration1CredentialMaster(institutionId);
        if (master == null) {
            master = InstitutionSignKey.provisioning(
                UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, now);
            keys.insert(master);
            masterCreated = true;
        }

        boolean versionCreated = false;
        if (versions.findBySignKeyIdAndVersion(master.id(), SignKeyNaming.LEGACY_KEY_VERSION).isEmpty()) {
            versions.insert(InstitutionSignKeyVersion.legacy(
                UUID.randomUUID(), master.id(), institutionId, legacyJwk, now));
            versionCreated = true;
        }

        int credentials = backfill.linkCredentials(institutionId, master.id());
        int statusLists = backfill.linkStatusLists(institutionId, master.id());
        return new Result(institutionId, masterCreated, versionCreated, credentials, statusLists);
    }

    /**
     * 세대가 바뀌거나(2세대 발급) 1세대가 COMPROMISED 로 더 이상 current 가 아니어도, 전환 이전
     * 발급분의 kid 는 항상 1세대를 가리키므로 버전 0 행은 1세대 마스터에 붙여야 한다. `findCurrent`
     * 를 쓰면 세대 교체 후 없거나(재삽입 시 세대 유니크 제약 위반) 엉뚱한 세대에 붙는다.
     */
    private InstitutionSignKey findGeneration1CredentialMaster(UUID institutionId) {
        return keys.findByInstitutionId(institutionId).stream()
            .filter(key -> key.purpose() == SignKeyPurpose.CREDENTIAL && key.generation() == 1)
            .findFirst()
            .orElse(null);
    }

    /** 플랫폼 키 JWK. kid 는 저장하지 않으므로 뺀다 (§6.2 규칙으로 조회 시 조립). */
    private String legacyPublicKeyJwk() {
        PublicKey publicKey = platformPublicKey.get();
        if (publicKey == null) {
            throw new IllegalStateException("platform public key is not configured");
        }
        Map<String, String> jwk = new LinkedHashMap<>(
            jwkFactory.create(SignKeyNaming.LEGACY_KID_FRAGMENT, publicKey));
        jwk.remove("kid");
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : jwk.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        return json.append('}').toString();
    }
}
