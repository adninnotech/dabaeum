package com.adn.dabaeum.credential.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionSignKeyRepository {

    void insert(InstitutionSignKey key);

    Optional<InstitutionSignKey> findById(UUID id);

    /** 발급·서명에 쓰는 현재 세대. 기관·목적당 최대 하나 (§11.1 부분 유니크). */
    Optional<InstitutionSignKey> findCurrent(UUID institutionId, SignKeyPurpose purpose);

    /** 세대·목적 순. 발급자 문서가 모든 세대의 버전을 모을 때 쓴다. */
    List<InstitutionSignKey> findByInstitutionId(UUID institutionId);

    /**
     * version_no 낙관적 잠금으로 갱신한다.
     *
     * @return 갱신했으면 true. 다른 프로세스가 먼저 바꿔 version_no 가 다르면 false
     */
    boolean update(InstitutionSignKey key, int expectedVersionNo);
}
