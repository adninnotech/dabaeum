package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebGateway;
import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebSession;
import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebUnavailableException;
import com.adn.dabaeum.attendance.config.DadaeguAttendProperties;
import com.adn.dabaeum.authentication.application.DadaeguLoginProperties;
import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * 학생이 강사 QR(URL)을 찍으면 그 요청마다 다대구 세션을 하나 열고, 학생 폰이 앱에서 돌아와
 * 물을 때 다대구에 결과를 확인해 출결을 기록한다. 세션이 학생마다 따로 생기므로 같은 QR 을
 * 반 전체가 찍을 수 있다.
 *
 * <p>다대구에 묻는 시점은 공식 {@code didLogin.js} 와 같다 — 앱이 앞에 있는 동안은 묻지 않고,
 * 폰이 돌아와 status 를 부를 때 한 번. 여기에 안 돌아오는 학생을 위한 보험으로
 * {@code pollTimeout/2}·{@code pollTimeout} 시점에 두 번만 더 확인한다.
 * 예전엔 서버 워커가 2초마다 90초 동안 물어 학생 한 명에 요청 45개가 나갔다.
 *
 * <p>세션은 메모리에만 둔다. 서버가 내려가면 진행 중인 인증만 잃고, 이미 기록된 출결은 DB 에 있다.
 * ponytail: 단일 인스턴스 가정. 스케일아웃하면 Redis 로 옮긴다.
 */
@Service
@Profile({"local", "dev"})
public class DefaultDadaeguAttendSessionService implements DadaeguAttendSessionService {

    private static final Logger log =
        LoggerFactory.getLogger(DefaultDadaeguAttendSessionService.class);

    /** 만료를 학생이 한 번은 볼 수 있도록 바로 지우지 않고 이만큼 남겨 둔다. */
    private static final long RETENTION_MINUTES = 5;

    private final DadaeguMobileWebGateway gateway;
    private final AttendanceQrTokenApplicationService qrTokenService;
    private final DadaeguLoginService loginService;
    private final AttendanceApplicationService attendanceService;
    private final UserRepository userRepository;
    private final Clock clock;
    private final DadaeguAttendProperties properties;
    private final Supplier<String> siteId;
    /** 보험용 지연 확인만 건다. 폴링 스레드가 아니다. */
    private final TaskScheduler taskScheduler;

    private final Map<UUID, Entry> sessions = new ConcurrentHashMap<>();

    @Autowired
    public DefaultDadaeguAttendSessionService(
        DadaeguMobileWebGateway gateway,
        AttendanceQrTokenApplicationService qrTokenService,
        DadaeguLoginService loginService,
        AttendanceApplicationService attendanceService,
        UserRepository userRepository,
        Clock clock,
        DadaeguAttendProperties properties,
        DadaeguLoginProperties loginProperties,
        TaskScheduler taskScheduler
    ) {
        this(gateway, qrTokenService, loginService, attendanceService, userRepository, clock,
            properties, Objects.requireNonNull(loginProperties, "loginProperties")::siteId,
            taskScheduler);
    }

    /** siteId 를 공급자로 받는다 — 테스트가 RSA 키를 만들지 않아도 되게. */
    public DefaultDadaeguAttendSessionService(
        DadaeguMobileWebGateway gateway,
        AttendanceQrTokenApplicationService qrTokenService,
        DadaeguLoginService loginService,
        AttendanceApplicationService attendanceService,
        UserRepository userRepository,
        Clock clock,
        DadaeguAttendProperties properties,
        Supplier<String> siteId,
        TaskScheduler taskScheduler
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.qrTokenService = Objects.requireNonNull(qrTokenService, "qrTokenService");
        this.loginService = Objects.requireNonNull(loginService, "loginService");
        this.attendanceService = Objects.requireNonNull(attendanceService, "attendanceService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.siteId = Objects.requireNonNull(siteId, "siteId");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
    }

    @Override
    public AttendSessionView open(String rawToken) {
        // 회차를 모른 채 토큰만 왔다. 토큰이 회차를 서명해 담고 있으므로 null 로 검증한다.
        ValidatedAttendanceQrToken validated = qrTokenService.validate(rawToken, null);
        DadaeguMobileWebSession opened;
        try {
            opened = gateway.init(siteId.get());
        } catch (DadaeguMobileWebUnavailableException exception) {
            log.warn("다대구 세션 발급 실패", exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.INTERNAL_SERVER_ERROR, "Dadaegu is unavailable");
        }
        // 검증 결과를 그대로 들고 간다. 나중에 다시 검증하면 인증에 시간을 끈 학생이
        // 토큰 만료로 거부된다(TTL 30초 < 세션 90초).
        Entry entry = new Entry(UUID.randomUUID(), validated,
            opened.sessionKey(), opened.deepLink(),
            clock.instant().plus(properties.pollTimeout()));
        sessions.put(entry.id, entry);
        // 시각은 스케줄러의 시계로 잰다. 주입된 Clock 은 테스트에서 고정돼 있어 지연 계산에 못 쓴다.
        Instant now = taskScheduler.getClock().instant();
        taskScheduler.schedule(() -> check(entry), now.plus(properties.pollTimeout().dividedBy(2)));
        taskScheduler.schedule(() -> {
            check(entry);
            expire(entry);
        }, now.plus(properties.pollTimeout()));
        log.info("다대구 출석 세션 시작 attendId={} sessionId={}", entry.id, entry.validated.sessionId());
        return entry.view();
    }

    @Override
    public Optional<AttendSessionView> status(UUID attendId) {
        purgeExpired();
        Entry entry = attendId == null ? null : sessions.get(attendId);
        if (entry == null) {
            return Optional.empty();
        }
        check(entry);
        return Optional.of(entry.view());
    }

    /**
     * 다대구에 묻는 유일한 곳. 폰이 status 를 부를 때와 보험 2회. 같은 세션은 pollInterval 안에
     * 다시 묻지 않고(새로고침 연타 방어), 이미 묻는 중이면 기다리지 않고 지금 상태를 돌려준다 —
     * 다대구 호출은 최악 30초라 락을 쥐고 기다리면 폰 요청과 보험 스케줄러가 같이 멈춘다.
     * 어떤 예외로 끝나든 상태를 확정한다. DID 암호문과 sessionKey 는 로그에 남기지 않는다.
     */
    private void check(Entry entry) {
        if (!entry.claim(properties.pollInterval())) {
            return;
        }
        try {
            gateway.poll(siteId.get(), entry.sessionKey)
                .ifPresent(encryptedDid -> recordFor(entry, encryptedDid));
        } catch (DadaeguMobileWebUnavailableException exception) {
            log.debug("다대구 확인 일시 실패 attendId={}", entry.id);   // 다음에 물을 때 다시
        } catch (RuntimeException exception) {
            log.error("다대구 출석 확인이 예외로 끝남 attendId={}", entry.id, exception);
            entry.errorCode.compareAndSet(null, ApiErrorCode.INTERNAL_SERVER_ERROR.name());
            entry.state.compareAndSet(AttendState.WAITING, AttendState.FAILED);
        } finally {
            entry.release();
        }
    }

    private void expire(Entry entry) {
        if (entry.state.compareAndSet(AttendState.WAITING, AttendState.EXPIRED)) {
            log.info("다대구 출석 세션 만료 attendId={}", entry.id);
        }
    }

    private void recordFor(Entry entry, String encryptedDid) {
        UUID userId;
        try {
            userId = loginService.resolveActiveUserId(encryptedDid);
        } catch (ApiException exception) {
            fail(entry, exception);
            return;
        }
        userRepository.findById(userId)
            .ifPresent(user -> entry.studentName.set(user.name()));
        try {
            attendanceService.recordDadaegu(
                new RecordDadaeguAttendanceCommand(userId, entry.validated));
        } catch (ApiException exception) {
            fail(entry, exception);
            return;
        }
        entry.state.set(AttendState.RECORDED);
        log.info("다대구 출석 기록 attendId={} sessionId={}", entry.id, entry.validated.sessionId());
    }

    private void fail(Entry entry, ApiException exception) {
        entry.errorCode.set(exception.code().name());
        entry.state.set(AttendState.FAILED);
        log.info("다대구 출석 실패 attendId={} code={}", entry.id, exception.code());
    }

    private void purgeExpired() {
        Instant cutoff = clock.instant().minusSeconds(RETENTION_MINUTES * 60);
        sessions.values().removeIf(entry -> entry.expiresAt.isBefore(cutoff));
    }

    private static final class Entry {

        private final UUID id;
        /** 학생이 찍는 순간 검증된 QR 토큰. 회차·발급자(강사)가 여기 들어 있다. */
        private final ValidatedAttendanceQrToken validated;
        private final String sessionKey;
        private final String deepLink;
        private final Instant expiresAt;
        private final AtomicReference<AttendState> state =
            new AtomicReference<>(AttendState.WAITING);
        private final AtomicReference<String> studentName = new AtomicReference<>();
        private final AtomicReference<String> errorCode = new AtomicReference<>();
        /** 지금 다대구에 묻는 중인가. 한 세션에 한 번만 나가게 하는 표. */
        private final AtomicBoolean asking = new AtomicBoolean();
        /** 마지막으로 다대구에 물은 시각(nanoTime). 표를 쥔 스레드만 쓴다. */
        private volatile long lastAskedNanos;

        private Entry(UUID id, ValidatedAttendanceQrToken validated, String sessionKey,
            String deepLink, Instant expiresAt) {
            this.id = id;
            this.validated = validated;
            this.sessionKey = sessionKey;
            this.deepLink = deepLink;
            this.expiresAt = expiresAt;
        }

        /**
         * 지금 다대구에 물어도 되면 표를 주고 true. 아직 기다리는 세션이고, 앞선 질문이
         * 끝났고, 마지막 질문에서 interval 이 지났을 때만. 표를 받은 스레드는 끝나고
         * {@link #release()} 를 불러야 한다.
         */
        private boolean claim(Duration interval) {
            if (state.get() != AttendState.WAITING || !asking.compareAndSet(false, true)) {
                return false;
            }
            if (lastAskedNanos != 0
                && System.nanoTime() - lastAskedNanos < interval.toNanos()) {
                asking.set(false);
                return false;
            }
            lastAskedNanos = System.nanoTime();
            return true;
        }

        private void release() {
            asking.set(false);
        }

        private AttendSessionView view() {
            return new AttendSessionView(id, state.get(), studentName.get(), errorCode.get(),
                deepLink);
        }
    }
}
