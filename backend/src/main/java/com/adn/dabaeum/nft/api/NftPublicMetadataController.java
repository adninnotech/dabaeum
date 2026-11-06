package com.adn.dabaeum.nft.api;

import com.adn.dabaeum.nft.application.NftPublicMetadataService;
import com.adn.dabaeum.nft.domain.NftTargetType;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대구체인 NFT 의 uri 가 가리키는 공개 메타데이터 (docs/specs/daeguchain-nft-issuance.md 4절).
 * 인증 없이 열리고, 원문 바이트를 바꾸지 않고 돌려준다. 폐기 반영이 늦지 않도록 캐시는 5분이다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/public/nft")
public class NftPublicMetadataController {

    /** 해시는 UTF-8 바이트로 계산했다. 응답도 UTF-8 로 고정해 체인의 해시와 바이트가 같게 한다. */
    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final NftPublicMetadataService service;

    public NftPublicMetadataController(NftPublicMetadataService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping("/badges/{issuanceId}")
    public ResponseEntity<String> badge(@PathVariable UUID issuanceId) {
        return ok(service.metadata(NftTargetType.BADGE, issuanceId));
    }

    @GetMapping("/certificates/{issuanceId}")
    public ResponseEntity<String> certificate(@PathVariable UUID issuanceId) {
        return ok(service.metadata(NftTargetType.CREDENTIAL, issuanceId));
    }

    private static ResponseEntity<String> ok(String json) {
        return ResponseEntity.ok().contentType(JSON_UTF8).cacheControl(CACHE).body(json);
    }
}
