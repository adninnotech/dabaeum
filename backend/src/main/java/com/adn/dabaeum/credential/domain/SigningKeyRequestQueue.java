package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 기관 서명키 생성 요청 outbox(tb_outbox_events, INSTITUTION_SIGNING_KEYS_REQUESTED)를 읽는 쪽.
 * claim 은 행을 잠그고 next_retry_at 을 leaseUntil 로 미뤄 커밋한다. OpenBao 호출은 그 뒤 트랜잭션 밖에서 한다.
 * 결과 기록은 그 lease 를 아직 쥐고 있을 때만 반영되고, 잃었으면(다른 서버가 다시 가져감) false 다.
 */
public interface SigningKeyRequestQueue {

    List<PendingSigningKeyRequest> claim(int limit, Instant now, Instant leaseUntil);

    boolean markDone(UUID eventId, Instant leaseUntil, Instant doneAt);

    boolean reschedule(UUID eventId, Instant leaseUntil, int retryCount, Instant nextRetryAt, String failureCode);

    boolean markFailed(UUID eventId, Instant leaseUntil, int retryCount, String failureCode);
}
