package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyCommands.CreateKeys;
import com.adn.dabaeum.credential.domain.PendingSigningKeyRequest;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SigningKeyRequestQueue;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 기관 승인·생성이 쌓은 서명키 생성 요청을 처리한다 (설계서 §3.1 "기관 등록 시 키 생성").
 *
 * <p>OpenBao 에 쓰기 전에 키를 한 번 읽어 닿는지 확인한다. 읽기가 실패하면 작업 기록을 만들지 않았으므로
 * 확실히 미실행이고, 요청을 미뤄 다시 시도한다(봉인 해제를 기다리는 경우). 읽기가 되면
 * {@link SigningKeyProvisioningService} 로 만든다. 그 뒤의 실패·결과 불확실은 재시도하지 않는다 —
 * 불확실한 생성은 운영자 판정 대상이다 (§15.1). 처리 중 예기치 못한 오류는 같은 간격으로 미룬다 — 다시 가져가도
 * 생성 서비스가 같은 멱등 키를 IDEMPOTENCY_CONFLICT 로 막으므로 OpenBao 를 다시 부르지 않는다.
 */
public class InstitutionSigningKeyProvisioner {

    /** OpenBao 호출 동안 다른 서버가 같은 요청을 가져가지 않게 거는 시간. 소유권 lease(5분)보다 길다. */
    static final Duration LEASE = Duration.ofMinutes(10);
    /** n 번째 미룸 뒤의 대기. 다섯 번째 실패에서 요청을 FAILED 로 끝낸다. */
    private static final List<Duration> RETRY_DELAYS = List.of(
        Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
    private static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;
    private static final String OPENBAO_UNAVAILABLE = "OPENBAO_UNAVAILABLE";
    private static final String OPENBAO_REJECTED = "OPENBAO_REJECTED";
    private static final String OPENBAO_LOGIN_FAILED = "OPENBAO_LOGIN_FAILED";
    private static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";
    private static final String REASON = "기관 승인·생성에 따른 자동 생성";

    private static final Logger log = LoggerFactory.getLogger(InstitutionSigningKeyProvisioner.class);

    private final SigningKeyRequestQueue queue;
    private final SigningKeyProvisioningService provisioning;
    private final TransitKeyAdminPort keyAdmin;
    private final Clock clock;

    public InstitutionSigningKeyProvisioner(
        SigningKeyRequestQueue queue,
        SigningKeyProvisioningService provisioning,
        TransitKeyAdminPort keyAdmin,
        Clock clock
    ) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.provisioning = Objects.requireNonNull(provisioning, "provisioning");
        this.keyAdmin = Objects.requireNonNull(keyAdmin, "keyAdmin");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void runOnce(int limit) {
        Instant now = clock.instant();
        // DB 는 마이크로초까지 저장한다. 결과 기록의 lease 비교가 어긋나지 않게 맞춘다.
        Instant leaseUntil = now.plus(LEASE).truncatedTo(ChronoUnit.MICROS);
        for (PendingSigningKeyRequest request : queue.claim(limit, now, leaseUntil)) {
            Outcome outcome;
            try {
                outcome = process(request);
            } catch (RuntimeException exception) {
                log.error("기관 서명키 자동 생성 중 예기치 못한 오류 institutionId={} eventId={}",
                    request.institutionId(), request.eventId(), exception);
                outcome = defer(request, UNEXPECTED_ERROR);
            }
            record(request, leaseUntil, outcome);
        }
    }

    private Outcome process(PendingSigningKeyRequest request) {
        try {
            keyAdmin.readKey(SignKeyPurpose.CREDENTIAL.mount(),
                SignKeyNaming.keyName(request.institutionId(), 1));
        } catch (TransitKeyAdminException exception) {
            if (exception.reason() == TransitKeyAdminException.Reason.AUTHENTICATION_FAILED) {
                return deferUntilLoginRecovers(request);
            }
            if (exception.reason() == TransitKeyAdminException.Reason.REJECTED) {
                log.error("OpenBao 가 키 조회를 거부해 기관 서명키 자동 생성을 멈춘다 — 설정 확인 필요 institutionId={}",
                    request.institutionId(), exception);
                return Outcome.failed(request.retryCount(), OPENBAO_REJECTED);
            }
            log.warn("OpenBao 에 닿지 않아 기관 서명키 생성을 미룬다 institutionId={} attempt={} reason={}",
                request.institutionId(), request.retryCount() + 1, exception.getMessage());
            return defer(request, OPENBAO_UNAVAILABLE);
        }
        try {
            provisioning.createKeys(new CreateKeys(request.institutionId(), request.requestedBy(),
                "institution-auto-" + request.eventId(), REASON));
            log.info("기관 서명키 자동 생성 완료 institutionId={}", request.institutionId());
        } catch (SigningKeyException exception) {
            if (exception.kind() != SigningKeyException.Kind.ALREADY_EXISTS) {
                log.error("기관 서명키 자동 생성 실패 — 운영자 확인 필요 institutionId={} kind={} operationId={}",
                    request.institutionId(), exception.kind(), exception.operationId(), exception);
                return Outcome.failed(request.retryCount(), "SIGNING_KEY_" + exception.kind().name());
            }
        }
        return Outcome.DONE;
    }

    /**
     * 로그인 실패는 자격(Secret ID)을 갱신하면 풀린다. 쓰기 전이라 안전하므로 포기하지 않고 최대 간격(2시간)으로
     * 계속 미룬다. 갱신 뒤 다음 시도에서 저절로 만들어진다.
     */
    private Outcome deferUntilLoginRecovers(PendingSigningKeyRequest request) {
        int attempts = request.retryCount() + 1;
        log.error("OpenBao 로그인 실패로 기관 서명키 생성을 미룬다 — AppRole Secret ID 확인 필요 institutionId={} attempt={}",
            request.institutionId(), attempts);
        Duration delay = RETRY_DELAYS.get(Math.min(request.retryCount(), RETRY_DELAYS.size() - 1));
        return new Outcome(Outcome.Kind.RESCHEDULE, attempts, OPENBAO_LOGIN_FAILED, clock.instant().plus(delay));
    }

    /** OpenBao 에 쓰기 전의 실패라 다시 시도해도 안전하다. 다섯 번째에서 멈춘다. */
    private Outcome defer(PendingSigningKeyRequest request, String code) {
        int attempts = request.retryCount() + 1;
        if (attempts >= MAX_ATTEMPTS) {
            log.error("기관 서명키 자동 생성을 포기한다 — 수동 생성 필요 institutionId={} code={}",
                request.institutionId(), code);
            return Outcome.failed(attempts, code);
        }
        return new Outcome(Outcome.Kind.RESCHEDULE, attempts, code,
            clock.instant().plus(RETRY_DELAYS.get(request.retryCount())));
    }

    /**
     * 결과 기록이 실패하거나 lease 를 잃었으면 그대로 둔다. lease 가 끝나면 다시 가져가며, 키가 이미 만들어졌으면
     * ALREADY_EXISTS 로 완료된다.
     */
    private void record(PendingSigningKeyRequest request, Instant leaseUntil, Outcome outcome) {
        try {
            boolean recorded = switch (outcome.kind()) {
                case DONE -> queue.markDone(request.eventId(), leaseUntil, clock.instant());
                case RESCHEDULE -> queue.reschedule(request.eventId(), leaseUntil, outcome.retryCount(),
                    outcome.nextRetryAt(), outcome.code());
                case FAILED -> queue.markFailed(request.eventId(), leaseUntil, outcome.retryCount(),
                    outcome.code());
            };
            if (!recorded) {
                log.warn("기관 서명키 생성 요청의 lease 를 잃어 결과를 기록하지 않는다 eventId={} outcome={}",
                    request.eventId(), outcome.kind());
            }
        } catch (RuntimeException exception) {
            log.error("기관 서명키 생성 요청 결과를 기록하지 못했다 — lease 만료 뒤 다시 처리된다 eventId={} outcome={}",
                request.eventId(), outcome.kind(), exception);
        }
    }

    private record Outcome(Kind kind, int retryCount, String code, Instant nextRetryAt) {

        static final Outcome DONE = new Outcome(Kind.DONE, 0, null, null);

        static Outcome failed(int retryCount, String code) {
            return new Outcome(Kind.FAILED, retryCount, code, null);
        }

        enum Kind { DONE, RESCHEDULE, FAILED }
    }
}
