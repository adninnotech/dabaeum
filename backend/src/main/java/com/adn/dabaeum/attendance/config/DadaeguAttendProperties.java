package com.adn.dabaeum.attendance.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 다대구 모바일웹 로그인을 서버에서 대신 부르는 설정. siteId 는 기존
 * {@code dabaeum.security.dadaegu.site-id} 를 그대로 쓴다.
 *
 * <p>{@code attendBaseUrl} 은 QR 이 가리키는, <b>학생 폰에서 닿는</b> 우리 주소다.
 * 로컬 검증에서는 PC 의 LAN IP 를 넣는다 — {@code --dabaeum.attendance.dadaegu.attend-base-url=http://192.168.0.10:8080}.
 * localhost 로 두면 폰에서 열리지 않는다.
 *
 * <p>{@code pollInterval} 은 같은 세션을 다대구에 다시 묻기까지의 최소 간격(폰 새로고침 연타 방어),
 * {@code pollTimeout} 은 세션 수명이다. 서버는 주기적으로 폴링하지 않는다 — 폰이 물을 때와
 * {@code pollTimeout/2}·{@code pollTimeout} 시점 보험 2회만 다대구에 간다.
 *
 * <p>ponytail: 공식 서버 API 가 아니라 모바일웹 경로다. 다대구가 바꾸면 여기 값부터 의심한다.
 */
@ConfigurationProperties(prefix = "dabaeum.attendance.dadaegu")
public record DadaeguAttendProperties(
    @DefaultValue("https://diddapp.daegu.go.kr") String baseUrl,
    @DefaultValue("http://localhost:8080") String siteOrigin,
    @DefaultValue("http://localhost:8080") String attendBaseUrl,
    @DefaultValue("OnlyDid") String requiredVc,
    @DefaultValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/128.0 Safari/537.36") String userAgent,
    @DefaultValue("3s") Duration pollInterval,
    @DefaultValue("90s") Duration pollTimeout
) {

    public DadaeguAttendProperties {
        baseUrl = requireUrl(baseUrl, "base-url");
        siteOrigin = requireUrl(siteOrigin, "site-origin");
        attendBaseUrl = requireUrl(attendBaseUrl, "attend-base-url");
        if (requiredVc == null || requiredVc.isBlank()) {
            throw new IllegalArgumentException(
                "dabaeum.attendance.dadaegu.required-vc is required");
        }
        requiredVc = requiredVc.trim();
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException(
                "dabaeum.attendance.dadaegu.user-agent is required");
        }
        requirePositive(pollInterval, "poll-interval");
        requirePositive(pollTimeout, "poll-timeout");
        if (pollInterval.compareTo(pollTimeout) > 0) {
            throw new IllegalArgumentException(
                "dabaeum.attendance.dadaegu.poll-interval must not exceed poll-timeout");
        }
    }

    public String initUrl() {
        return baseUrl + "/dgbp/mobileWeb/login/init";
    }

    public String chkUrl() {
        return baseUrl + "/dgbp/mobileWeb/login/chk";
    }

    /** QR 에 넣을 학생 진입 주소. 프론트가 이 문자열을 QR 로 그린다. */
    public String attendUrl(String rawToken) {
        return attendBaseUrl + "/attend?t="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private static String requireUrl(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "dabaeum.attendance.dadaegu." + name + " is required");
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                "dabaeum.attendance.dadaegu." + name + " must be positive");
        }
    }
}
