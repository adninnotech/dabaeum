package com.adn.dabaeum.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebGateway;
import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebSession;
import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebUnavailableException;
import com.adn.dabaeum.attendance.config.DadaeguAttendProperties;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

/**
 * 학생 한 명분 세션의 상태 전이. 다대구엔 status 가 불릴 때와 보험 시점(pollTimeout/2·pollTimeout)에만
 * 묻는다. 보험은 별도 스레드라 만료·보험 테스트는 상태를 기다려 확인한다.
 * 간격·수명을 밀리초로 줄여 실제 대기 없이 만료까지 본다.
 */
@ExtendWith(MockitoExtension.class)
class DadaeguAttendSessionServiceTest {

    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID TOKEN_ID = UUID.fromString(
        "88888888-8888-8888-8888-888888888888");
    private static final UUID INSTRUCTOR_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");
    private static final UUID USER_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999");
    private static final Instant NOW = Instant.parse("2026-09-08T01:00:00Z");
    private static final String RAW_TOKEN = "a".repeat(32);
    private static final String SITE_ID = "site-1";

    @Mock DadaeguMobileWebGateway gateway;
    @Mock AttendanceQrTokenApplicationService qrTokenService;
    @Mock DadaeguLoginService loginService;
    @Mock AttendanceApplicationService attendanceService;
    @Mock UserRepository userRepository;

    private DefaultDadaeguAttendSessionService service;
    private final SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();

    @AfterEach
    void tearDown() {
        taskScheduler.close();
    }

    @BeforeEach
    void setUp() {
        // 만료를 보려는 테스트만 예산을 짧게 준다. 나머지에 짧은 예산을 주면 부하가 걸린 CI 에서
        // 워커가 스케줄되기 전에 시간이 끝나 플레이크가 된다(2026-09-08 실제로 겪음).
        givenPollTimeout(Duration.ofSeconds(5));
    }

    private void givenPollTimeout(Duration pollTimeout) {
        givenProperties(Duration.ofMillis(1), pollTimeout);
    }

    private void givenProperties(Duration pollInterval, Duration pollTimeout) {
        service = new DefaultDadaeguAttendSessionService(
            gateway,
            qrTokenService,
            loginService,
            attendanceService,
            userRepository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new DadaeguAttendProperties(
                "https://diddapp.example", "http://localhost:8080", "http://localhost:8080",
                "OnlyDid", "test-agent", pollInterval, pollTimeout),
            () -> SITE_ID,
            taskScheduler
        );
    }

    private ValidatedAttendanceQrToken validatedToken() {
        return new ValidatedAttendanceQrToken(
            TOKEN_ID, SESSION_ID, INSTRUCTOR_ID, NOW.minusSeconds(1), NOW.plusSeconds(59));
    }

    private void givenValidTokenAndSession() {
        when(qrTokenService.validate(RAW_TOKEN, null)).thenReturn(validatedToken());
        when(gateway.init(SITE_ID))
            .thenReturn(new DadaeguMobileWebSession("KEY-1", "didapp://x"));
    }

    @Test
    void openValidatesTokenThenIssuesPerStudentSession() {
        givenValidTokenAndSession();
        lenient().when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.empty());

        AttendSessionView view = service.open(RAW_TOKEN);

        assertThat(view.state()).isEqualTo(AttendState.WAITING);
        assertThat(view.deepLink()).isEqualTo("didapp://x");
        assertThat(service.status(view.id())).isPresent();
    }

    @Test
    void invalidTokenNeverReachesDadaegu() {
        when(qrTokenService.validate(RAW_TOKEN, null)).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_QR_INVALID, "invalid"));

        assertThatThrownBy(() -> service.open(RAW_TOKEN)).isInstanceOf(ApiException.class);

        verifyNoInteractions(gateway);
    }

    @Test
    void dadaeguOutageBecomes503() {
        when(qrTokenService.validate(RAW_TOKEN, null)).thenReturn(validatedToken());
        when(gateway.init(SITE_ID))
            .thenThrow(new DadaeguMobileWebUnavailableException("down", null));

        assertThatThrownBy(() -> service.open(RAW_TOKEN))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void recordsAttendanceWhenPollReturnsDidAndExposesStudentName() {
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.of("CIPHER"));
        when(loginService.resolveActiveUserId("CIPHER")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(attendanceService.recordDadaegu(
            new RecordDadaeguAttendanceCommand(USER_ID, validatedToken())))
            .thenReturn(attendance());

        AttendSessionView view = service.open(RAW_TOKEN);
        await(() -> state(view) == AttendState.RECORDED);

        assertThat(service.status(view.id()).orElseThrow().studentName()).isEqualTo("한상희");
    }

    @Test
    void exposesErrorCodeWhenRecordingFails() {
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.of("CIPHER"));
        when(loginService.resolveActiveUserId("CIPHER")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(attendanceService.recordDadaegu(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_CONFLICT, "duplicate"));

        AttendSessionView view = service.open(RAW_TOKEN);
        await(() -> state(view) == AttendState.FAILED);

        AttendSessionView failed = service.status(view.id()).orElseThrow();
        assertThat(failed.errorCode()).isEqualTo("ATTENDANCE_CONFLICT");
        // 이름은 남겨 둔다 — "이미 출석하셨습니다"를 누구에게 말하는지 보여야 한다.
        assertThat(failed.studentName()).isEqualTo("한상희");
    }

    @Test
    void unknownDidBecomesFailedWithoutRecording() {
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.of("CIPHER"));
        when(loginService.resolveActiveUserId("CIPHER")).thenThrow(new ApiException(
            HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_FAILED, "failed"));

        AttendSessionView view = service.open(RAW_TOKEN);
        await(() -> state(view) == AttendState.FAILED);

        assertThat(service.status(view.id()).orElseThrow().errorCode())
            .isEqualTo("AUTHENTICATION_FAILED");
        verifyNoInteractions(attendanceService);
    }

    /**
     * ApiException 이 아닌 예외가 나도 상태를 확정해야 한다. 안 그러면 워커 스레드만 죽고
     * 세션은 WAITING 으로 남아, 학생 폰에 "인증해 주세요"가 영원히 떠 있는다.
     */
    @Test
    void unexpectedFailureStillEndsTheSession() {
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.of("CIPHER"));
        when(loginService.resolveActiveUserId("CIPHER")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(attendanceService.recordDadaegu(any()))
            .thenThrow(new IllegalStateException("DB 연결 끊김"));

        AttendSessionView view = service.open(RAW_TOKEN);
        await(() -> state(view) == AttendState.FAILED);

        assertThat(service.status(view.id()).orElseThrow().errorCode())
            .isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void expiresWhenStudentNeverAuthenticates() {
        givenPollTimeout(Duration.ofMillis(200));
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.empty());

        AttendSessionView view = service.open(RAW_TOKEN);
        await(() -> state(view) == AttendState.EXPIRED);

        verifyNoInteractions(loginService, attendanceService);
    }

    /** 폴링이 일시적으로 실패해도 세션을 버리지 않는다 — 다음 주기에 다시 묻는다. */
    @Test
    void transientDadaeguFailureDoesNotKillTheSession() {
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1"))
            .thenThrow(new DadaeguMobileWebUnavailableException("blip", null))
            .thenReturn(Optional.of("CIPHER"));
        when(loginService.resolveActiveUserId("CIPHER")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(attendanceService.recordDadaegu(any())).thenReturn(attendance());

        AttendSessionView view = service.open(RAW_TOKEN);
        await(() -> state(view) == AttendState.RECORDED);
    }

    /** 핵심 변경: 폰이 묻기 전엔 다대구에 묻지 않는다. 예전 워커(주기 1ms)라면 이 사이 수십 번 물었다. */
    @Test
    void doesNotAskDadaeguUntilThePhoneAsks() throws InterruptedException {
        givenValidTokenAndSession();

        service.open(RAW_TOKEN);
        Thread.sleep(100);

        verify(gateway, never()).poll(anyString(), anyString());
    }

    /** 폰이 새로고침을 연타해도 다대구엔 pollInterval 에 한 번만 나간다. */
    @Test
    void asksDadaeguAtMostOncePerInterval() {
        givenProperties(Duration.ofSeconds(10), Duration.ofSeconds(60));
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.empty());

        AttendSessionView view = service.open(RAW_TOKEN);
        service.status(view.id());
        service.status(view.id());
        service.status(view.id());

        verify(gateway, times(1)).poll(SITE_ID, "KEY-1");
    }

    /** 학생이 앱에서 인증만 하고 브라우저로 안 돌아와도 출석은 남아야 한다(보험). */
    @Test
    void recordsEvenIfThePhoneNeverComesBack() {
        givenPollTimeout(Duration.ofMillis(200));
        givenValidTokenAndSession();
        when(gateway.poll(SITE_ID, "KEY-1")).thenReturn(Optional.of("CIPHER"));
        when(loginService.resolveActiveUserId("CIPHER")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(attendanceService.recordDadaegu(any())).thenReturn(attendance());

        service.open(RAW_TOKEN);   // status() 를 한 번도 부르지 않는다

        verify(attendanceService, timeout(2000)).recordDadaegu(any());
    }

    /**
     * 다대구가 느려도 폰의 다음 status 는 기다리지 않는다. 간격(1ms)은 이미 지났으므로
     * 막는 것은 "묻는 중" 표뿐이다 — 예전처럼 락 안에서 다대구를 부르면 여기서 멈춘다.
     */
    @Test
    @Timeout(30)   // 락이 돌아오면 두 번째 status 가 영영 안 끝난다 — CI 를 매달지 말고 실패시킨다
    void slowDadaeguDoesNotBlockTheNextStatus() throws InterruptedException {
        givenProperties(Duration.ofMillis(1), Duration.ofSeconds(60));
        givenValidTokenAndSession();
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gateway.poll(SITE_ID, "KEY-1")).thenAnswer(invocation -> {
            inFlight.countDown();
            release.await();
            return Optional.empty();
        });

        AttendSessionView view = service.open(RAW_TOKEN);
        Thread phone = new Thread(() -> service.status(view.id()));
        phone.setDaemon(true);   // 실패해도 테스트 JVM 이 이 스레드에 붙잡히지 않게
        phone.start();
        assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();

        Instant asked = Instant.now();
        assertThat(service.status(view.id())).isPresent();
        assertThat(Duration.between(asked, Instant.now())).isLessThan(Duration.ofSeconds(1));

        release.countDown();
        phone.join(5000);
        verify(gateway, times(1)).poll(SITE_ID, "KEY-1");
    }

    private AttendState state(AttendSessionView view) {
        return service.status(view.id()).map(AttendSessionView::state).orElse(null);
    }

    private void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("상태 전이가 5초 안에 일어나지 않았다");
    }

    private User user() {
        return new User(USER_ID, "한상희", null, null, null, UserStatus.ACTIVE,
            null, NOW, NOW, null, null, null, null, null, true, false, null);
    }

    private Attendance attendance() {
        return new Attendance(ATTENDANCE_ID, COURSE_ID, SESSION_ID, ENROLLMENT_ID, TOKEN_ID,
            AttendanceMethod.QR_DADAEGU, AttendanceStatus.PRESENT, NOW,
            AttendanceSource.APP, INSTRUCTOR_ID, NOW, NOW);
    }
}
