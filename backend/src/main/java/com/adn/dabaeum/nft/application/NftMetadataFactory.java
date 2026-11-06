package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftMetadata;
import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftTargetType;
import com.adn.dabaeum.nft.domain.NftUriMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * NFT 메타데이터 원문과 해시를 만든다. 원문은 장부에 저장해 두고 공개 API 가 그대로 내주므로,
 * 체인에 적힌 해시와 항상 같다. 개인정보는 넣지 않는다.
 */
public final class NftMetadataFactory {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String PLATFORM_NAME = "다배움";

    private final ObjectMapper objectMapper;
    private final NftUriMode mode;

    public NftMetadataFactory(ObjectMapper objectMapper, NftUriMode mode) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /** 이 모드에서 지금 메타데이터를 만들 수 있는가. 주소 방식은 공개 도메인이 있어야 한다. */
    public boolean canBuild(String baseUrl) {
        return mode == NftUriMode.URN || baseUrl != null;
    }

    /**
     * @param baseUrl 공개 origin. 주소 방식에서는 필수이고 토큰 uri 가 된다. 식별 문자열 방식에서는 원문 안의
     *                이미지 주소에만 쓰고, 없으면 이미지를 넣지 않는다
     */
    public NftMetadata build(NftSource source, UUID issuanceId, String baseUrl) {
        if (!canBuild(baseUrl)) {
            throw new IllegalArgumentException("baseUrl is required in URL mode");
        }
        String base = baseUrl == null ? null : trimSlash(baseUrl);
        boolean credential = source.targetType() == NftTargetType.CREDENTIAL;
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", credential ? source.title() + " 수료증" : source.title());
        if (source.description() != null && !source.description().isBlank()) {
            document.put("description", source.description());
        }
        if (source.imageFileId() != null && base != null) {
            document.put("image", base + "/api/v1/files/" + source.imageFileId() + "/content");
        }
        document.put("attributes", attributes(source, credential));
        String json = objectMapper.writeValueAsString(document);
        String kind = credential ? "certificates" : "badges";
        String uri = mode == NftUriMode.URN
            ? "urn:dabaeum:nft:" + kind + ":" + issuanceId
            : base + "/api/v1/public/nft/" + kind + "/" + issuanceId;
        return new NftMetadata(uri, json, sha256(json));
    }

    private static List<Map<String, String>> attributes(NftSource source, boolean credential) {
        List<Map<String, String>> attributes = new ArrayList<>();
        attributes.add(attribute("종류", credential ? "수료증" : "학습 배지"));
        attributes.add(attribute("발급 기관", source.issuerName() == null ? PLATFORM_NAME : source.issuerName()));
        attributes.add(attribute("발급일", LocalDate.ofInstant(source.issuedAt(), SEOUL).toString()));
        if (source.contentHash() != null) {
            attributes.add(attribute("수료증 해시", source.contentHash()));
        }
        return attributes;
    }

    private static Map<String, String> attribute(String trait, String value) {
        Map<String, String> attribute = new LinkedHashMap<>();
        attribute.put("trait_type", trait);
        attribute.put("value", value);
        return attribute;
    }

    private static String trimSlash(String value) {
        String text = value.trim();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
