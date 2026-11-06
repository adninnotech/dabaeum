package com.adn.dabaeum.nft.infrastructure.daeguchain;

import java.net.URI;
import java.time.Duration;

/**
 * 대구체인 NFT API 접속 정보와 발급 지갑. 개인키와 토큰은 로그·{@link #toString()} 에 남기지 않는다.
 *
 * @param baseUrl         서버 origin. 경로 접두 {@code /daeguchain/v2/mitum} 은 구현이 붙인다
 * @param token           App Key 로 발급한 Token
 * @param chain           문서 기본값 dchain
 * @param ownerAddress    발급 지갑 주소. 컨트랙트 소유자이자 토큰 소유자다
 * @param ownerPrivateKey 발급 지갑 개인키. mint 서명에 쓴다
 * @param readTimeout     BaaS 가 처리 확인을 20초까지 기다리므로 그보다 길어야 한다
 */
public record DaeguChainNftProperties(
    String baseUrl,
    String token,
    String chain,
    String ownerAddress,
    String ownerPrivateKey,
    Duration readTimeout
) {

    private static final Duration BAAS_CONFIRM_WAIT = Duration.ofSeconds(20);

    public DaeguChainNftProperties {
        baseUrl = origin(baseUrl);
        token = required(token, "token");
        chain = chain == null || chain.isBlank() ? "dchain" : chain.trim();
        ownerAddress = required(ownerAddress, "ownerAddress");
        ownerPrivateKey = required(ownerPrivateKey, "ownerPrivateKey");
        if (readTimeout == null || readTimeout.compareTo(BAAS_CONFIRM_WAIT) <= 0) {
            throw new IllegalArgumentException("readTimeout must be longer than " + BAAS_CONFIRM_WAIT);
        }
    }

    @Override
    public String toString() {
        return "DaeguChainNftProperties[baseUrl=" + baseUrl + ", chain=" + chain
            + ", ownerAddress=" + ownerAddress + ", token=****, ownerPrivateKey=****, readTimeout="
            + readTimeout + "]";
    }

    private static String origin(String value) {
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
