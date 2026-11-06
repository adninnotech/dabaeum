package com.adn.dabaeum.nft.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.nft.application.NftPublicMetadataService;
import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftMetadata;
import com.adn.dabaeum.nft.domain.NftTargetType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NftPublicMetadataController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=nft-public-test-token")
@Import({
    NftPublicMetadataService.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class NftPublicMetadataControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");
    private static final String JSON =
        "{\"name\":\"첫 수료 배지\",\"attributes\":[{\"trait_type\":\"종류\",\"value\":\"학습 배지\"}]}";

    @Autowired MockMvc mockMvc;
    @MockitoBean NftIssuanceRepository issuances;

    private NftIssuance submitted(NftTargetType type) {
        UUID id = UUID.randomUUID();
        return NftIssuance.pending(id, type, UUID.randomUUID(),
                new NftMetadata("https://dabaeum.example/api/v1/public/nft/badges/" + id, JSON, "a".repeat(64)), NOW)
            .submitting("0xBADGEfca", NOW).submitted("FACT", NOW);
    }

    @Test
    void servesTheStoredBytesWithoutLoginAndLetsClientsCacheBriefly() throws Exception {
        NftIssuance row = submitted(NftTargetType.BADGE);
        when(issuances.findById(row.id())).thenReturn(Optional.of(row));

        mockMvc.perform(get("/api/v1/public/nft/badges/{id}", row.id()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(JSON))
            .andExpect(header().string("Cache-Control", "max-age=300, public"));
    }

    @Test
    void responseBytesHashToTheValueWrittenOnChain() throws Exception {
        NftMetadata metadata = new com.adn.dabaeum.nft.application.NftMetadataFactory(
            new tools.jackson.databind.ObjectMapper(), com.adn.dabaeum.nft.domain.NftUriMode.URN).build(new com.adn.dabaeum.nft.domain.NftSource(
                NftTargetType.CREDENTIAL, UUID.randomUUID(), NOW, "데이터 분석 기초", null, "대구평생학습원", null,
                "e".repeat(64)), UUID.randomUUID(), "https://dabaeum.example");
        UUID id = UUID.randomUUID();
        NftIssuance row = NftIssuance.pending(id, NftTargetType.CREDENTIAL, UUID.randomUUID(), metadata, NOW)
            .submitting("0xCERTfca", NOW).submitted("FACT", NOW);
        when(issuances.findById(id)).thenReturn(Optional.of(row));

        byte[] body = mockMvc.perform(get("/api/v1/public/nft/certificates/{id}", id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
            .andReturn().getResponse().getContentAsByteArray();

        String sha256 = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(body));
        org.assertj.core.api.Assertions.assertThat(sha256).isEqualTo(metadata.hash());
    }

    @Test
    void revokedTokenIsGone() throws Exception {
        NftIssuance row = submitted(NftTargetType.CREDENTIAL);
        NftIssuance revoked = new NftIssuance(row.id(), row.targetType(), row.targetId(), row.status(),
            row.contractAddress(), row.metadataUri(), row.metadataJson(), row.metadataHash(), row.factHash(),
            row.tokenIndex(), row.blockHeight(), row.confirmedAt(), row.retryCount(), row.nextAttemptAt(),
            row.errorCode(), NOW, row.createdAt(), row.updatedAt());
        when(issuances.findById(row.id())).thenReturn(Optional.of(revoked));

        mockMvc.perform(get("/api/v1/public/nft/certificates/{id}", row.id()))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("NFT_METADATA_REVOKED"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("첫 수료"))));
    }

    @Test
    void wrongKindNotSentOrUnknownIdIsNotFound() throws Exception {
        NftIssuance badge = submitted(NftTargetType.BADGE);
        when(issuances.findById(badge.id())).thenReturn(Optional.of(badge));
        UUID queuedId = UUID.randomUUID();
        when(issuances.findById(queuedId)).thenReturn(Optional.of(NftIssuance.pending(queuedId,
            NftTargetType.BADGE, UUID.randomUUID(),
            new NftMetadata("https://dabaeum.example/x", JSON, "a".repeat(64)), NOW)));
        UUID missing = UUID.randomUUID();
        when(issuances.findById(missing)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/public/nft/certificates/{id}", badge.id()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NFT_METADATA_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/public/nft/badges/{id}", queuedId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/nft/badges/{id}", missing)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/nft/badges/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
