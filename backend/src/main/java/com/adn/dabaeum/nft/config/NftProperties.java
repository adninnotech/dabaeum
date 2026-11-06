package com.adn.dabaeum.nft.config;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 대구체인 NFT 발행 설정 (docs/specs/daeguchain-nft-issuance.md). 개인키·토큰은 {@link #toString()} 에 남기지 않는다.
 *
 * @param enabled                    발행 워커 사용 여부. 꺼져 있으면 아무 빈도 만들지 않는다
 * @param ledger                     {@code fake}(메모리) 또는 {@code daeguchain}(실제 체인)
 * @param startAt                    이 시각 이후 발급분만 NFT 로 남긴다. 그 전 발급분은 옮기지 않는다
 * @param uriMode                    {@code urn}(식별 문자열, 기본) 또는 {@code url}(웹 주소, 도메인 허용 필요)
 * @param metadataBaseUrl            공개 origin. url 모드에서는 필수(https)이고 비우면 "도메인 대기"로 쌓는다.
 *                                   urn 모드에서는 원문 안 이미지 주소에만 쓰므로 http 도 된다
 * @param walletAddress              발급 지갑. 컨트랙트 소유자이자 토큰 소유자
 * @param walletPrivateKey           발급 지갑 개인키. ledger=daeguchain 일 때만 필요
 * @param badgeContractAddress       배지 컨트랙트
 * @param certificateContractAddress 수료증 컨트랙트
 */
@ConfigurationProperties(prefix = "dabaeum.nft")
public record NftProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("fake") String ledger,
    @DefaultValue("urn") String uriMode,
    Instant startAt,
    String metadataBaseUrl,
    String walletAddress,
    String walletPrivateKey,
    String badgeContractAddress,
    String certificateContractAddress,
    @DefaultValue Daeguchain daeguchain,
    @DefaultValue Worker worker
) {

    public static final String FAKE = "fake";
    public static final String DAEGUCHAIN = "daeguchain";
    public static final String URN = "urn";
    public static final String URL = "url";

    public NftProperties {
        ledger = ledger == null || ledger.isBlank() ? FAKE : ledger.trim();
        uriMode = uriMode == null || uriMode.isBlank() ? URN : uriMode.trim();
        metadataBaseUrl = blankToNull(metadataBaseUrl);
        walletAddress = blankToNull(walletAddress);
        walletPrivateKey = blankToNull(walletPrivateKey);
        badgeContractAddress = blankToNull(badgeContractAddress);
        certificateContractAddress = blankToNull(certificateContractAddress);
        if (!FAKE.equals(ledger) && !DAEGUCHAIN.equals(ledger)) {
            throw new IllegalArgumentException("dabaeum.nft.ledger must be fake or daeguchain");
        }
        if (!URN.equals(uriMode) && !URL.equals(uriMode)) {
            throw new IllegalArgumentException("dabaeum.nft.uri-mode must be urn or url");
        }
        if (metadataBaseUrl != null) {
            boolean https = metadataBaseUrl.startsWith("https://");
            if (URL.equals(uriMode) ? !https : !(https || metadataBaseUrl.startsWith("http://"))) {
                throw new IllegalArgumentException(URL.equals(uriMode)
                    ? "dabaeum.nft.metadata-base-url must be https in url mode"
                    : "dabaeum.nft.metadata-base-url must be an http(s) origin");
            }
        }
        if (enabled) {
            required(startAt, "start-at");
            required(walletAddress, "wallet-address");
            required(badgeContractAddress, "badge-contract-address");
            required(certificateContractAddress, "certificate-contract-address");
            if (DAEGUCHAIN.equals(ledger)) {
                required(walletPrivateKey, "wallet-private-key");
            }
        }
    }

    @Override
    public String toString() {
        return "NftProperties[enabled=" + enabled + ", ledger=" + ledger + ", uriMode=" + uriMode + ", startAt=" + startAt
            + ", metadataBaseUrl=" + metadataBaseUrl + ", walletAddress=" + walletAddress
            + ", walletPrivateKey=****, badgeContractAddress=" + badgeContractAddress
            + ", certificateContractAddress=" + certificateContractAddress + ", daeguchain=" + daeguchain
            + ", worker=" + worker + "]";
    }

    /** 대구체인 접속. Storage 와 같은 서버·토큰을 쓴다. */
    public record Daeguchain(
        @DefaultValue("https://www.daegu.go.kr") String baseUrl,
        String token,
        @DefaultValue("dchain") String chain,
        @DefaultValue("40s") Duration readTimeout
    ) {
        @Override
        public String toString() {
            return "Daeguchain[baseUrl=" + baseUrl + ", chain=" + chain + ", token=****, readTimeout=" + readTimeout + "]";
        }
    }

    public record Worker(
        @DefaultValue("10s") Duration fixedDelay,
        @DefaultValue("20") int batchSize,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("5m") Duration retryBaseDelay,
        @DefaultValue("10m") Duration submittingStaleAfter
    ) {
        public Worker {
            if (batchSize < 1) {
                throw new IllegalArgumentException("dabaeum.nft.worker.batch-size must be positive");
            }
        }
    }

    public com.adn.dabaeum.nft.domain.NftUriMode mode() {
        return URL.equals(uriMode) ? com.adn.dabaeum.nft.domain.NftUriMode.URL : com.adn.dabaeum.nft.domain.NftUriMode.URN;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void required(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("dabaeum.nft." + name + " is required when dabaeum.nft.enabled=true");
        }
    }
}
