package com.adn.dabaeum.credential.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * 자격증명의 비동기 블록체인 앵커 요청을 기록하는 포트이다.
 *
 * <p>인프라 어댑터가 애플리케이션 패키지에 역방향으로 의존하지 않도록 이 포트는
 * 도메인 경계에 둔다.</p>
 */
public interface BlockchainRequestPort {

    Optional<PendingBlockchainRequest> findByIdempotencyKey(
        String network,
        String idempotencyKey
    );

    void createAnchor(PendingBlockchainRequest request);

    boolean hasPendingOperationForGroup(UUID credentialGroupId);
}
