package com.adn.dabaeum.attendance.infrastructure.dadaegu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 다대구 모바일웹 응답을 읽는 순수 함수만 검증한다. HTTP 왕복은 실제 서버가 필요하므로
 * {@code docs/ops/dadaegu-attendance-check/README.md} 의 수동 절차로 확인한다.
 */
class JdkDadaeguMobileWebGatewayTest {

    @Test
    void buildsDeepLinkFromInitResponse() {
        String link = JdkDadaeguMobileWebGateway.deepLink(
            "KEY-1", "https://diddapp.example/x", "OnlyDid");

        assertThat(link).startsWith("didapp://mobileweblogin?");
        assertThat(link).contains("sessionKey=KEY-1");
        assertThat(link).contains("flag=dqrp");
        assertThat(link).contains("requiredVC=OnlyDid");
        assertThat(link).contains("subVC=");
    }

    @Test
    void extractsDidFromCompletedChkResponse() {
        assertThat(JdkDadaeguMobileWebGateway.didOf(
            "{\"success\":true,\"returnUrl\":\"https://x\","
                + "\"returnData\":\"{\\\"did\\\":\\\"CIPHER\\\"}\"}"))
            .contains("CIPHER");
    }

    /** returnData 는 객체로도, JSON 문자열로도 온다(2026-09-07 실측). 둘 다 읽어야 한다. */
    @Test
    void acceptsReturnDataAsObjectNotOnlyAsJsonString() {
        assertThat(JdkDadaeguMobileWebGateway.didOf(
            "{\"success\":true,\"returnUrl\":\"https://x\",\"returnData\":{\"did\":\"CIPHER\"}}"))
            .contains("CIPHER");
    }

    @Test
    void returnsEmptyWhileStudentHasNotAuthenticatedYet() {
        assertThat(JdkDadaeguMobileWebGateway.didOf(
            "{\"success\":true,\"returnUrl\":\"Failed\",\"returnData\":null}")).isEmpty();
        assertThat(JdkDadaeguMobileWebGateway.didOf("{\"success\":false}")).isEmpty();
        assertThat(JdkDadaeguMobileWebGateway.didOf("not json")).isEmpty();
        assertThat(JdkDadaeguMobileWebGateway.didOf(
            "{\"success\":true,\"returnUrl\":\"https://x\",\"returnData\":{\"did\":\"\"}}"))
            .isEmpty();
    }
}
