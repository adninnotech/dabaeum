package com.adn.dabaeum.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestRateLimiter;
import com.adn.dabaeum.user.application.LearnerLookupService;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 한 학습자(학번 또는 이메일)를 24시간에 10번까지만 조회한다. 결과를 보기 전에 세므로 동시에 보내도 10번에서 막히고,
 * 맞게 찾아도 줄지 않는다 — 다른 사람의 성공이 생년월일을 맞히던 사람의 횟수를 지우지 않게.
 */
class LearnerLookupKeyLimitTest {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PHONE = "01012345678";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final LearnerLookupService service = mock(LearnerLookupService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final LearnerLookupController controller =
        new LearnerLookupController(service, currentUser, clock, new RequestRateLimiter(clock));

    @Test
    void tenLookupsPerKeyThenLockedForEveryAccountAndAddressEvenAfterSuccess() {
        // 실패와 성공이 섞여도 모두 센다. 성공이 횟수를 지우지 않는다.
        when(service.lookup(any(), any(), any(), any(), eq(PHONE)))
            .thenThrow(notFound()).thenThrow(notFound()).thenReturn(learner());
        when(currentUser.requireContext()).thenAnswer(invocation -> admin());
        for (int i = 0; i < LearnerLookupController.LOOKUPS_PER_KEY; i++) {
            call(PHONE, "192.0.2." + i);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThatThrownBy(() -> controller.lookup(INSTITUTION_ID, body(PHONE),
                request("198.51.100.7"), blocked))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(error.code()).isEqualTo(ApiErrorCode.RATE_LIMITED);
            });
        assertThat(blocked.getHeader("Retry-After")).isEqualTo(Long.toString(24 * 60 * 60));
        verify(service, times(LearnerLookupController.LOOKUPS_PER_KEY)).lookup(any(), any(), any(), any(), any());
    }

    @Test
    void simultaneousRequestsStillStopAtTheLimit() throws Exception {
        AtomicInteger reachedService = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        when(currentUser.requireContext()).thenAnswer(invocation -> admin());
        when(service.lookup(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            reachedService.incrementAndGet();
            release.await(5, TimeUnit.SECONDS);
            throw notFound();
        });

        int attempts = 30;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String address = "192.0.2." + i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    controller.lookup(INSTITUTION_ID, body(PHONE), request(address),
                        new MockHttpServletResponse());
                    return 200;
                } catch (ApiException error) {
                    return error.status().value();
                }
            }));
        }
        start.countDown();
        // 10개가 서비스 안에 붙잡혀 있는 동안 나머지는 서비스에 닿지 않고 429 로 끝나야 한다.
        int tooMany = attempts - LearnerLookupController.LOOKUPS_PER_KEY;
        Thread.sleep(200);
        release.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(5, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(reachedService.get()).isEqualTo(LearnerLookupController.LOOKUPS_PER_KEY);
        assertThat(statuses).filteredOn(status -> status == 429).hasSize(tooMany);
        assertThat(statuses).filteredOn(status -> status == 404).hasSize(LearnerLookupController.LOOKUPS_PER_KEY);
    }

    @Test
    void otherPhonesAreCountedSeparately() {
        when(currentUser.requireContext()).thenAnswer(invocation -> admin());
        when(service.lookup(any(), any(), any(), any(), any())).thenThrow(notFound());
        for (int i = 0; i < LearnerLookupController.LOOKUPS_PER_KEY; i++) {
            call(PHONE, "192.0.2.1");
        }

        assertThatThrownBy(() -> controller.lookup(INSTITUTION_ID, body("01099998888"),
                request("192.0.2.1"), new MockHttpServletResponse()))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void phoneKeyIgnoresHyphensLikeTheLookup() {
        when(currentUser.requireContext()).thenAnswer(invocation -> admin());
        when(service.lookup(any(), any(), any(), any(), any())).thenThrow(notFound());
        for (int i = 0; i < LearnerLookupController.LOOKUPS_PER_KEY; i++) {
            call(i % 2 == 0 ? "010-1234-5678" : " 01012345678 ", "192.0.2.1");
        }

        assertThatThrownBy(() -> controller.lookup(INSTITUTION_ID, body(PHONE),
                request("192.0.2.1"), new MockHttpServletResponse()))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void malformedPhonesAreNotCountedOrStored() {
        // 형식이 틀린 값은 서비스가 400 으로 돌려보낸다. 키로 쓰지 않아 메모리에 쌓이지도 않는다.
        when(currentUser.requireContext()).thenAnswer(invocation -> admin());
        when(service.lookup(any(), any(), any(), any(), any())).thenThrow(
            new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Invalid request"));
        for (int i = 0; i < LearnerLookupController.LOOKUPS_PER_KEY + 5; i++) {
            assertThatThrownBy(() -> controller.lookup(INSTITUTION_ID, body("12345"), request("192.0.2.1"),
                    new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    private void call(String phone, String address) {
        try {
            controller.lookup(INSTITUTION_ID, body(phone), request(address), new MockHttpServletResponse());
        } catch (ApiException error) {
            assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private static LearnerLookupRequest body(String phone) {
        return new LearnerLookupRequest("홍길동", "1990-01-15", phone);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "Learner not found");
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
            "/api/v1/institutions/" + INSTITUTION_ID + "/learners/lookup");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private static User learner() {
        return new User(UUID.randomUUID(), "홍길동", "hong@example.com", null, null, UserStatus.ACTIVE, null, NOW, NOW,
            null, null, null, null, null,
            User.DEFAULT_EMAIL_NOTIFICATION_ENABLED, User.DEFAULT_SMS_NOTIFICATION_ENABLED,
            null);
    }
}
