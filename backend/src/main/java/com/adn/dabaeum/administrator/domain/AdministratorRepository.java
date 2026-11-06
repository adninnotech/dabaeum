package com.adn.dabaeum.administrator.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 결과의 roles 는 비어 있다 — 서비스가 역할을 한 번에 채운다(N+1 방지). */
public interface AdministratorRepository {

    AdministratorPage search(AdministratorCriteria criteria);

    /** 역할 조건 없이 한 사람을 읽는다. 방금 만든 계정을 돌려줄 때 쓴다. */
    Optional<Administrator> findById(UUID userId, Instant now);
}
