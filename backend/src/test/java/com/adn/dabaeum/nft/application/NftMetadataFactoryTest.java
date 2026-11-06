package com.adn.dabaeum.nft.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.nft.domain.NftMetadata;
import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftTargetType;
import com.adn.dabaeum.nft.domain.NftUriMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NftMetadataFactoryTest {

    private static final UUID ISSUANCE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID IMAGE = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final ObjectMapper mapper = new ObjectMapper();
    private final NftMetadataFactory factory = new NftMetadataFactory(mapper, NftUriMode.URL);
    private final NftMetadataFactory urnFactory = new NftMetadataFactory(mapper, NftUriMode.URN);

    @Test
    void credentialMetadataNamesTheCourseAndIssuerWithTheVcHash() throws Exception {
        NftMetadata metadata = factory.build(new NftSource(NftTargetType.CREDENTIAL, UUID.randomUUID(),
            Instant.parse("2026-10-27T16:30:00Z"), "데이터 분석 기초", null, "대구평생학습원", null,
            "e".repeat(64)), ISSUANCE, "https://dabaeum.example/");

        assertThat(metadata.uri()).isEqualTo("https://dabaeum.example/api/v1/public/nft/certificates/" + ISSUANCE);
        JsonNode json = mapper.readTree(metadata.json());
        assertThat(json.path("name").asString()).isEqualTo("데이터 분석 기초 수료증");
        assertThat(json.has("image")).isFalse();
        assertThat(json.toString())
            .contains("대구평생학습원")
            // 발급일은 서울 시각 기준 날짜다 (UTC 16:30 = 서울 다음 날 01:30)
            .contains("2026-10-28")
            .contains("e".repeat(64));
        assertThat(metadata.hash()).isEqualTo(sha256(metadata.json()));
    }

    @Test
    void badgeMetadataPointsAtThePublicImage() throws Exception {
        NftMetadata metadata = factory.build(new NftSource(NftTargetType.BADGE, UUID.randomUUID(),
            Instant.parse("2026-10-28T01:00:00Z"), "학습왕 2단계", "강좌 5개 수료", null, IMAGE, null),
            ISSUANCE, "https://dabaeum.example");

        JsonNode json = mapper.readTree(metadata.json());
        assertThat(json.path("name").asString()).isEqualTo("학습왕 2단계");
        assertThat(json.path("description").asString()).isEqualTo("강좌 5개 수료");
        assertThat(json.path("image").asString())
            .isEqualTo("https://dabaeum.example/api/v1/files/" + IMAGE + "/content");
        assertThat(json.toString()).contains("다배움");
    }

    @Test
    void sameSourceAlwaysProducesTheSameBytes() {
        NftSource source = new NftSource(NftTargetType.BADGE, UUID.randomUUID(),
            Instant.parse("2026-10-28T01:00:00Z"), "첫 수료", null, null, null, null);

        assertThat(factory.build(source, ISSUANCE, "https://a.example").json())
            .isEqualTo(factory.build(source, ISSUANCE, "https://a.example").json());
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void urnModeWritesAnIdentifierThatIsNotAWebAddress() throws Exception {
        NftMetadata metadata = urnFactory.build(new NftSource(NftTargetType.CREDENTIAL, UUID.randomUUID(),
            Instant.parse("2026-10-28T01:00:00Z"), "데이터 분석 기초", null, "대구평생학습원", null, "e".repeat(64)),
            ISSUANCE, null);

        // 대구시 게이트웨이가 외부 웹 주소를 막으므로 체인에는 식별 문자열만 적는다 (설계서 4절).
        assertThat(metadata.uri()).isEqualTo("urn:dabaeum:nft:certificates:" + ISSUANCE);
        assertThat(metadata.hash()).isEqualTo(sha256(metadata.json()));
    }

    @Test
    void urnModeStillLinksTheImageWhenAPublicBaseIsKnown() throws Exception {
        NftSource badge = new NftSource(NftTargetType.BADGE, UUID.randomUUID(), Instant.parse("2026-10-28T01:00:00Z"),
            "첫 수료", null, null, IMAGE, null);

        JsonNode withBase = mapper.readTree(urnFactory.build(badge, ISSUANCE, "http://118.45.182.100:8088").json());
        JsonNode withoutBase = mapper.readTree(urnFactory.build(badge, ISSUANCE, null).json());

        assertThat(urnFactory.build(badge, ISSUANCE, null).uri()).isEqualTo("urn:dabaeum:nft:badges:" + ISSUANCE);
        assertThat(withBase.path("image").asString())
            .isEqualTo("http://118.45.182.100:8088/api/v1/files/" + IMAGE + "/content");
        assertThat(withoutBase.has("image")).isFalse();
    }
}
