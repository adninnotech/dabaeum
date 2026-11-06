package com.adn.dabaeum.publiccourse.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터 강좌 수집 설정 (설계서 §7). {@code serviceKey} 는 Decoding 키이며 비어 있으면 수집이 시작 전에
 * 실패로 끝난다(앱 기동은 막지 않는다).
 */
@ConfigurationProperties(prefix = "dabaeum.public-courses")
public record PublicCourseProperties(
    Boolean enabled,
    String baseUrl,
    String serviceKey,
    Integer pageSize,
    Duration readTimeout,
    Duration requestPause,
    Duration staleRunAfter,
    List<String> institutionCodes
) {

    static final String DEFAULT_BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_public_lftm_lrn_lctre_api";

    public PublicCourseProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip();
        serviceKey = serviceKey == null || serviceKey.isBlank() ? null : serviceKey.strip();
        pageSize = pageSize == null ? 1000 : pageSize;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        requestPause = requestPause == null ? Duration.ofMillis(200) : requestPause;
        staleRunAfter = staleRunAfter == null ? Duration.ofHours(1) : staleRunAfter;
        institutionCodes = institutionCodes == null ? List.of() : List.copyOf(institutionCodes);
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("dabaeum.public-courses.page-size must be 1..1000");
        }
        if (readTimeout.isNegative() || requestPause.isNegative() || !staleRunAfter.isPositive()) {
            throw new IllegalArgumentException("dabaeum.public-courses durations are invalid");
        }
    }

    public boolean hasServiceKey() {
        return serviceKey != null;
    }

    /** 서비스키를 로그·예외에 흘리지 않도록 문자열 표현에서 가린다. */
    @Override
    public String toString() {
        return "PublicCourseProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
            + ", serviceKey=" + (serviceKey == null ? "<none>" : "<masked>") + ", pageSize=" + pageSize
            + ", institutionCodes=" + institutionCodes + "]";
    }
}
