package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.PendingSigningKeyRequest;
import com.adn.dabaeum.credential.domain.SigningKeyRequestQueue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "dev"})
public class SigningKeyRequestQueueMyBatisRepository implements SigningKeyRequestQueue {

    private final SigningKeyRequestQueueMapper mapper;

    public SigningKeyRequestQueueMyBatisRepository(SigningKeyRequestQueueMapper mapper) {
        this.mapper = mapper;
    }

    /** 잠근 행에 lease 를 걸고 커밋해 다른 서버가 OpenBao 호출 동안 같은 요청을 가져가지 않게 한다. */
    @Override
    @Transactional
    public List<PendingSigningKeyRequest> claim(int limit, Instant now, Instant leaseUntil) {
        List<PendingSigningKeyRequest> claimed = mapper.selectPendingForUpdate(limit, now).stream()
            .map(row -> new PendingSigningKeyRequest(row.id(), UUID.fromString(row.institutionId()),
                UUID.fromString(row.requestedBy()), row.retryCount()))
            .toList();
        claimed.forEach(request -> mapper.lease(request.eventId(), leaseUntil));
        return claimed;
    }

    @Override
    public boolean markDone(UUID eventId, Instant leaseUntil, Instant doneAt) {
        return mapper.markDone(eventId, leaseUntil, doneAt) == 1;
    }

    @Override
    public boolean reschedule(
        UUID eventId, Instant leaseUntil, int retryCount, Instant nextRetryAt, String failureCode
    ) {
        return mapper.reschedule(eventId, leaseUntil, retryCount, nextRetryAt, failureCode) == 1;
    }

    @Override
    public boolean markFailed(UUID eventId, Instant leaseUntil, int retryCount, String failureCode) {
        return mapper.markFailed(eventId, leaseUntil, retryCount, failureCode) == 1;
    }
}
