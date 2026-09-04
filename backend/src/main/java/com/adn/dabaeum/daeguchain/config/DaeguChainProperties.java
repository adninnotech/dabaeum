package com.adn.dabaeum.daeguchain.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 대구체인 Storage API(또는 같은 계약의 에뮬레이터) 접속 정보다. 값은 로그에 남기지 않는다.
 *
 * @param baseUrl   서버 origin. 에뮬레이터는 {@code http://127.0.0.1:8090}, 실제 대구체인은
 *                  {@code https://www.daegu.go.kr}. 경로 접두 {@code /daeguchain/v2/mitum/storage}
 *                  는 클라이언트가 붙인다.
 * @param token     요청 본문 {@code token}. App Key 로 발급한 Token. 비워두면 보내지 않는다.
 * @param chain     요청 본문 {@code chain}. 문서 기본값 dchain
 * @param projectId Storage 프로젝트 ID. 에뮬레이터 {@code regist_project} 로 만든 값
 * @param healthUrl 블록 높이를 읽을 상태 URL. 에뮬레이터 {@code /health} 전용이며 실제 대구체인에는
 *                  없으므로 비워둔다. 비우면 모니터링의 블록 높이는 표시하지 않는다.
 */
@ConfigurationProperties(prefix = "dabaeum.daeguchain")
public record DaeguChainProperties(
    String baseUrl,
    String token,
    @DefaultValue("dchain") String chain,
    String projectId,
    String healthUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout
) {

    public DaeguChainProperties {
        baseUrl = requireOrigin(baseUrl);
        token = token == null || token.isBlank() ? null : token.trim();
        chain = required(chain, "chain");
        projectId = required(projectId, "projectId");
        healthUrl = healthUrl == null || healthUrl.isBlank() ? null : healthUrl.trim();
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout is invalid");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("readTimeout is invalid");
        }
    }

    private static String requireOrigin(String value) {
        String text = required(value, "baseUrl");
        URI uri = URI.create(text);
        if (!uri.isAbsolute() || uri.getHost() == null
            || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an absolute http(s) origin");
        }
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
