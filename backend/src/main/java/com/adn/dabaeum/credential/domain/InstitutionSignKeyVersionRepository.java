package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionSignKeyVersionRepository {

    void insert(InstitutionSignKeyVersion version);

    /** publicKeyJwk 는 jsonb 정규화(키 순서·공백)를 거친 텍스트로 돌아온다. JSON 동치이며 텍스트 동일성은 보장하지 않는다. */
    Optional<InstitutionSignKeyVersion> findById(UUID id);

    Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersion(UUID signKeyId, int keyVersion);

    /**
     * §6.5 즉시 동작. 버전 행을 FOR UPDATE 로 잠가 발급 확정(§8.1 10단계, FOR SHARE)과 직렬화한다. 호출자의
     * 트랜잭션 안에서만 부르고, 그 뒤 같은 트랜잭션에서 tb_credentials 를 잠그지 않는다 (SigningKeyFinalizeCheck 의
     * 잠금 순서, 교착 방지).
     */
    Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersionForUpdate(UUID signKeyId, int keyVersion);

    /** 검증 3단계: kid 조각으로 세대·버전 행을 찾는다 (§6.2, §9.2). */
    Optional<InstitutionSignKeyVersion> findByInstitutionIdAndKidFragment(
        UUID institutionId, String kidFragment);

    /** key_version 오름차순. */
    List<InstitutionSignKeyVersion> findBySignKeyId(UUID signKeyId);

    /** 마스터 세대·목적, 버전 오름차순. 발급자 문서가 쓴다 (§9.2). */
    List<InstitutionSignKeyVersion> findByInstitutionId(UUID institutionId);

    /**
     * §6.2 T2 의 조건부 RETIRED 전이. {@code WHERE status = 'ACTIVE'} 라 COMPROMISED 를 건드리지 않는다.
     *
     * @return 갱신했으면 true
     */
    boolean retireIfActive(UUID id, Instant retiredAt);

    /**
     * §6.5 즉시 동작. {@code WHERE status <> 'COMPROMISED'} 라 두 번째 선언은 0행이다.
     *
     * @return 갱신했으면 true
     */
    boolean compromise(UUID id, Instant approvedCompromisedAt);

    /**
     * §8.1 10단계. 버전 행을 FOR SHARE 로 읽는다. 발급끼리는 서로 막지 않고 유출 선언(FOR UPDATE)과만
     * 직렬화된다. 호출자의 트랜잭션 안에서 불러야 잠금이 확정 커밋까지 유지된다.
     */
    Optional<SignKeyVersionStatus> findStatusForShare(UUID signKeyId, int keyVersion);
}
