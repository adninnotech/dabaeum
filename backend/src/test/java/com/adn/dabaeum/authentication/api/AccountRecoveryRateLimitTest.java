package com.adn.dabaeum.authentication.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.adn.dabaeum.authentication.application.AccountRecoveryService;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountRecoveryRateLimitTest {

    @Test
    void fourthEmailRequestStillReturnsAcceptedWithoutSending() {
        AccountRecoveryService service = mock(AccountRecoveryService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);
        AccountRecoveryController controller = new AccountRecoveryController(
            service, mock(CurrentUserProvider.class), clock, new RequestRateLimiter(clock));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        for (int i = 0; i < 4; i++) {
            assertThat(controller.requestReset(new PasswordResetRequestRequest(" Test@Example.com "),
                servletRequest).getStatusCode().value()).isEqualTo(202);
        }
        verify(service, times(3)).requestPasswordReset(" Test@Example.com ");
    }
}
