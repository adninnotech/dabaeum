package com.adn.dabaeum.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 코드 리뷰 MEDIUM 후속: IP 제한만으로는 기관 관리자 한 계정이 IP 를 바꿔 가며 평생학번을 대입하는 것을
 * 막지 못한다. 계정마다 하루 횟수를 따로 센다.
 */
class LearnerLookupAccountLimitTest {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final LearnerLookupService service = mock(LearnerLookupService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final LearnerLookupController controller =
        new LearnerLookupController(service, currentUser, clock, new RequestRateLimiter(clock));

    @Test
    void eachAccountGetsADailyQuotaRegardlessOfAddress() {
        AuthenticatedUserContext admin = admin();
        when(currentUser.requireContext()).thenReturn(admin);
        when(service.lookup(any(), any(), any(), any(), any())).thenReturn(learner());

        for (int i = 0; i < LearnerLookupController.DAILY_LOOKUPS_PER_ACCOUNT; i++) {
            controller.lookup(INSTITUTION_ID, body(i), request("192.0.2." + (i % 250)),
                new MockHttpServletResponse());
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThatThrownBy(() -> controller.lookup(INSTITUTION_ID, body(99_999), request("198.51.100.9"), blocked))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(error.code()).isEqualTo(ApiErrorCode.RATE_LIMITED);
            });
        assertThat(blocked.getHeader("Retry-After")).isEqualTo(Long.toString(24 * 60 * 60));
        verify(service, times(LearnerLookupController.DAILY_LOOKUPS_PER_ACCOUNT)).lookup(any(), any(), any(), any(), any());
    }

    @Test
    void anotherAccountIsNotAffected() {
        when(service.lookup(any(), any(), any(), any(), any())).thenReturn(learner());
        when(currentUser.requireContext()).thenReturn(admin());
        for (int i = 0; i < LearnerLookupController.DAILY_LOOKUPS_PER_ACCOUNT; i++) {
            controller.lookup(INSTITUTION_ID, body(i), request("192.0.2.1"), new MockHttpServletResponse());
        }

        when(currentUser.requireContext()).thenReturn(admin());
        assertThat(controller.lookup(INSTITUTION_ID, body(99_999), request("192.0.2.1"),
            new MockHttpServletResponse()).data().name()).isEqualTo("홍길동");
    }

    /** 학습자별 제한(하루 10회)에 먼저 걸리지 않게 매번 다른 번호로 조회한다. */
    private static LearnerLookupRequest body(int seq) {
        return new LearnerLookupRequest("홍길동", "1990-01-15", "0101%07d".formatted(seq));
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
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
