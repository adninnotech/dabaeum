package com.adn.dabaeum.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestRateLimitConfigurationTest {

    @Test
    void loginUsesRemoteAddressNotClientSuppliedForwardingHeader() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);
        RequestRateLimitConfiguration config = new RequestRateLimitConfiguration(clock);
        for (int i = 0; i < 20; i++) {
            assertThat(config.check(request("192.0.2.1", "spoof-" + i),
                new MockHttpServletResponse())).isTrue();
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThatThrownBy(() -> config.check(request("192.0.2.1", "new-spoof"), blocked))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.RATE_LIMITED));
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(config.check(request("192.0.2.2", "spoof-0"),
            new MockHttpServletResponse())).isTrue();
    }

    @Test
    void learnerLookupIsLimitedPerAddressAcrossInstitutions() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);
        RequestRateLimitConfiguration config = new RequestRateLimitConfiguration(clock);
        for (int i = 0; i < 30; i++) {
            // 기관 id 를 바꿔 가며 불러도 같은 IP 면 한 카운터를 쓴다.
            assertThat(config.check(lookup("192.0.2.1", java.util.UUID.randomUUID()),
                new MockHttpServletResponse())).isTrue();
        }
        assertThatThrownBy(() -> config.check(lookup("192.0.2.1", java.util.UUID.randomUUID()),
            new MockHttpServletResponse()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.RATE_LIMITED));
        assertThat(config.check(lookup("192.0.2.2", java.util.UUID.randomUUID()),
            new MockHttpServletResponse())).isTrue();
    }

    private MockHttpServletRequest lookup(String remoteAddr, java.util.UUID institutionId) {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST", "/api/v1/institutions/" + institutionId + "/learners/lookup");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private MockHttpServletRequest request(String remoteAddr, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
