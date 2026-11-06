package com.adn.dabaeum.nft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftMetadata;
import com.adn.dabaeum.nft.domain.NftSource;
import com.adn.dabaeum.nft.domain.NftTargetType;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftIssuanceMapper;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftIssuanceMyBatisRepository;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftSourceMapper;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftSourceMyBatisRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** NFT 발행 장부와 원천 SQL. 가짜 원장을 쓰므로 체인에는 아무것도 쓰지 않는다. */
class NftIssuanceRepositoryIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final String VC_HASH = "c".repeat(64);
    private static final NftMetadata METADATA =
        new NftMetadata("https://dabaeum.example/api/v1/public/nft/badges/x", "{\"name\":\"x\"}", "d".repeat(64));

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired SqlSessionTemplate sqlSession;
    @Autowired NftIssuanceMapper issuanceMapper;
    @Autowired NftSourceMapper sourceMapper;

    private NftIssuanceMyBatisRepository issuances;
    private NftSourceMyBatisRepository sources;
    // 이미 있는 dev 데이터와 섞이지 않도록 미래 시각을 기준으로 쓴다.
    private final Instant now = Instant.now().plus(3650, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

    @BeforeEach
    void setUp() {
        issuances = new NftIssuanceMyBatisRepository(issuanceMapper);
        sources = new NftSourceMyBatisRepository(sourceMapper);
    }

    @Test
    void issuedCredentialIsFoundOnceAndCarriesNoPersonalData() {
        UUID credentialId = insertIssuedCredential();

        List<NftSource> found = sources.findUnrecorded(NftTargetType.CREDENTIAL, now, 50);

        assertThat(found).extracting(NftSource::targetId).contains(credentialId);
        NftSource source = found.stream().filter(s -> s.targetId().equals(credentialId)).findFirst().orElseThrow();
        assertThat(source.title()).startsWith("course-title-");
        assertThat(source.issuerName()).startsWith("institution-name-");
        assertThat(source.contentHash()).isEqualTo(VC_HASH);

        assertThat(issuances.insertIfAbsent(NftIssuance.pending(UUID.randomUUID(), NftTargetType.CREDENTIAL,
            credentialId, METADATA, now))).isTrue();
        assertThat(issuances.insertIfAbsent(NftIssuance.pending(UUID.randomUUID(), NftTargetType.CREDENTIAL,
            credentialId, METADATA, now))).isFalse();
        sqlSession.clearCache();
        assertThat(sources.findUnrecorded(NftTargetType.CREDENTIAL, now, 50))
            .extracting(NftSource::targetId).doesNotContain(credentialId);
        assertThat(sources.findUnrecorded(NftTargetType.CREDENTIAL, now.plusSeconds(1), 50))
            .extracting(NftSource::targetId).doesNotContain(credentialId);
    }

    @Test
    void tieredBadgeUsesTheCurrentLevelNameAndImage() {
        UUID levelImage = UUID.randomUUID();
        UUID definition = insertTieredDefinition(levelImage);
        UUID badgeId = insertBadge(definition, 2, "ISSUED");

        NftSource source = sources.find(NftTargetType.BADGE, badgeId).orElseThrow();

        assertThat(source.title()).isEqualTo("단계 배지 실버");
        assertThat(source.imageFileId()).isEqualTo(levelImage);
        assertThat(source.issuerName()).isNull();
        assertThat(sources.findUnrecorded(NftTargetType.BADGE, now, 50)).extracting(NftSource::targetId)
            .contains(badgeId);
    }

    @Test
    void claimMovesDueRowsToSubmittingOnceWithTheContractForTheirType() {
        UUID badgeId = insertBadge(insertTieredDefinition(null), 1, "ISSUED");
        NftIssuance row = NftIssuance.pending(UUID.randomUUID(), NftTargetType.BADGE, badgeId, METADATA,
            now.minusSeconds(5));
        issuances.insertIfAbsent(row);

        List<NftIssuance> claimed = issuances.claimDue(500, now, "0xBADGEfca", "0xCERTfca");
        List<NftIssuance> again = issuances.claimDue(500, now, "0xBADGEfca", "0xCERTfca");

        NftIssuance mine = claimed.stream().filter(r -> r.id().equals(row.id())).findFirst().orElseThrow();
        assertThat(mine.status()).isEqualTo(NftIssuanceStatus.SUBMITTING);
        assertThat(mine.contractAddress()).isEqualTo("0xBADGEfca");
        assertThat(again).extracting(NftIssuance::id).doesNotContain(row.id());

        NftIssuance submitted = mine.submitted("FACT-" + UUID.randomUUID(), now);
        assertThat(issuances.update(NftIssuanceStatus.SUBMITTING, submitted)).isTrue();
        assertThat(issuances.update(NftIssuanceStatus.SUBMITTING, submitted)).isFalse();
        assertThat(issuances.findById(row.id())).map(NftIssuance::status).contains(NftIssuanceStatus.SUBMITTED);
    }

    @Test
    void revokedBadgeIsMarkedAndCancelledIfNotYetSent() {
        UUID definition = insertTieredDefinition(null);
        UUID sentBadge = insertBadge(definition, 1, "ISSUED");
        UUID queuedBadge = insertBadge(insertTieredDefinition(null), 1, "ISSUED");
        NftIssuance sent = NftIssuance.pending(UUID.randomUUID(), NftTargetType.BADGE, sentBadge, METADATA, now)
            .submitting("0xBADGEfca", now).submitted("FACT-" + UUID.randomUUID(), now);
        issuances.insertIfAbsent(sent);
        NftIssuance queued = NftIssuance.waitingDomain(UUID.randomUUID(), NftTargetType.BADGE, queuedBadge, now);
        issuances.insertIfAbsent(queued);
        jdbcTemplate.update("UPDATE tb_learning_badges SET status = 'REVOKED', revoked_at = ? WHERE id IN (?, ?)",
            offset(now), sentBadge, queuedBadge);

        issuances.markRevokedTargets(now);
        issuances.cancelRevokedBeforeSubmit(now);
        sqlSession.clearCache();

        NftIssuance sentAfter = issuances.findById(sent.id()).orElseThrow();
        assertThat(sentAfter.status()).isEqualTo(NftIssuanceStatus.SUBMITTED);
        assertThat(sentAfter.revokedAt()).isEqualTo(now);
        // 폐기 표시는 확인 순서·정체 판정에 쓰는 갱신 시각을 건드리지 않는다.
        assertThat(sentAfter.updatedAt()).isEqualTo(sent.updatedAt());

        Instant later = now.plusSeconds(30);
        assertThat(issuances.touch(sent.id(), NftIssuanceStatus.SUBMITTED, later)).isTrue();
        assertThat(issuances.touch(sent.id(), NftIssuanceStatus.PENDING, later)).isFalse();
        sqlSession.clearCache();
        assertThat(issuances.findById(sent.id())).map(NftIssuance::updatedAt).contains(later);
        assertThat(issuances.findById(queued.id())).map(NftIssuance::status).contains(NftIssuanceStatus.CANCELLED);
    }

    @Test
    void mintedRowWithoutATokenIndexIsRejectedByTheDatabase() {
        UUID badgeId = insertBadge(insertTieredDefinition(null), 1, "ISSUED");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO tb_nft_issuances (id, target_type, target_id, status, metadata_uri, metadata_json,
                metadata_hash, fact_hash, contract_address)
            VALUES (?, 'BADGE', ?, 'MINTED', 'https://x', '{}', ?, 'FACT', '0xBADGEfca')
            """, id, badgeId, "d".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertIssuedCredential() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, unique("inst"), unique("institution-name"));
        UUID userId = insertUser();
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity, status)
            VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20, 'COMPLETED')
            """, courseId, institutionId, unique("course"), unique("course-title"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at) "
            + "VALUES (?, ?, ?, 'APPROVED', ?)", enrollmentId, courseId, userId, offset(now));
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_completions (id, enrollment_id, status, attendance_rate, completed_minutes,
                evaluated_at, completed_at, confirmed_by, confirmed_at)
            VALUES (?, ?, 'COMPLETED', 100.00, 60, ?, ?, ?, ?)
            """, completionId, enrollmentId, offset(now), offset(now), userId, offset(now));
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_credential_groups (id, completion_id, created_at) VALUES (?, ?, ?)",
            groupId, completionId, offset(now));
        UUID credentialId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_credentials (id, credential_group_id, credential_no, version_no,
                issuer_identifier, subject_identifier, credential_type, status,
                valid_from, valid_until, vc_payload, vc_hash, issued_at, created_at, updated_at)
            VALUES (?, ?, ?, 1, ?, ?, 'LIFELONG_EDUCATION_COMPLETION', 'ISSUED', ?, ?, ?, ?, ?, ?, ?)
            """, credentialId, groupId, "CERT-" + UUID.randomUUID(), "urn:dabaeum:issuer:" + institutionId,
            "urn:dabaeum:subject:" + userId, offset(now), offset(now.plusSeconds(86_400)),
            "{\"credentialSubject\":{}}", VC_HASH, offset(now), offset(now), offset(now));
        return credentialId;
    }

    private UUID insertTieredDefinition(UUID silverImage) {
        UUID id = UUID.randomUUID();
        String levels = """
            {"classificationCode":null,"levels":[
              {"level":1,"name":"브론즈","minCount":1,"imageFileId":null},
              {"level":2,"name":"실버","minCount":3,"imageFileId":%s}]}
            """.formatted(silverImage == null ? "null" : "\"" + silverImage + "\"");
        jdbcTemplate.update("""
            INSERT INTO tb_badge_definitions (id, code, name, rule_type, rule_params, active)
            VALUES (?, ?, '단계 배지', 'TIERED', CAST(? AS jsonb), TRUE)
            """, id, "NFT_IT_" + id.toString().substring(0, 8).toUpperCase(), levels);
        return id;
    }

    private UUID insertBadge(UUID definitionId, int level, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_learning_badges (id, user_id, badge_type, badge_name, status, issued_at,
                created_at, updated_at, definition_id, tier_level)
            VALUES (?, ?, 'TIERED', '단계 배지', ?, ?, ?, ?, ?, ?)
            """, id, insertUser(), status, offset(now), offset(now), offset(now), definitionId, (short) level);
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = unique("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)", id, code, code + "@example.test");
        return id;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
