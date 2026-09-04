package com.adn.dabaeum.credential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.BlockchainRequestPort;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.PendingBlockchainRequest;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CredentialPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final String VC_HASH = "a".repeat(64);
    private static final String CHAIN_KEY = "A1B2C3D4E5F6G7H8";

    @Autowired
    CredentialGroupRepository groupRepository;

    @Autowired
    CredentialRepository credentialRepository;

    @Autowired
    BlockchainTransactionRepository transactionRepository;

    @Autowired
    BlockchainRequestPort blockchainRequestPort;

    @Test
    void roundTripsRegistryMetadataAndPreservesLegacyNulls() {
        Fixture currentFixture = insertCompletionFixture();
        Credential current = issuedCredential(currentFixture, CHAIN_KEY);
        credentialRepository.insert(current);

        assertThat(credentialRepository.findById(current.id())).contains(current);

        Credential revoked = current.markRevoked("course completion withdrawn", NOW.plusSeconds(1));
        credentialRepository.update(revoked);
        assertThat(credentialRepository.findById(current.id())).contains(revoked);

        Fixture legacyFixture = insertCompletionFixture();
        Credential legacy = pendingCredential(legacyFixture);
        credentialRepository.insert(legacy);

        assertThat(credentialRepository.findById(legacy.id())).get()
            .satisfies(found -> {
                assertThat(found.chainKey()).isNull();
                assertThat(found.vcHashVersion()).isNull();
            });
    }

    @Test
    void enforcesChainKeyPartialUniqueness() {
        Credential first = issuedCredential(insertCompletionFixture(), CHAIN_KEY);
        credentialRepository.insert(first);

        Credential duplicate = issuedCredential(insertCompletionFixture(), CHAIN_KEY);
        assertSqlState("23505", () -> credentialRepository.insert(duplicate));
    }

    @Test
    void rejectsInvalidChainKeyFormat() {
        Credential legacy = pendingCredential(insertCompletionFixture());
        credentialRepository.insert(legacy);
        assertSqlState("23514", () -> jdbcTemplate.update(
            "UPDATE tb_credentials SET chain_key = ? WHERE id = ?", "lowercase-invalid", legacy.id()));
    }

    @Test
    void rejectsUnsupportedHashVersion() {
        Credential legacy = pendingCredential(insertCompletionFixture());
        credentialRepository.insert(legacy);
        assertSqlState("23514", () -> jdbcTemplate.update(
            "UPDATE tb_credentials SET vc_hash_version = ? WHERE id = ?", "UNKNOWN", legacy.id()));
    }

    @Test
    void claimsOnlyLegacyTransactionsBeforeProviderAwareWorkerActivation() {
        BlockchainTransaction legacy = pendingTransaction("DABAEUM_FABRIC");
        BlockchainTransaction poc = pendingTransaction("FABRIC_POC");
        transactionRepository.insert(legacy);
        transactionRepository.insert(poc);

        assertThat(transactionRepository.claimDue(
                java.util.List.of("DABAEUM_FABRIC"), 10, NOW))
            .extracting(BlockchainTransaction::network)
            .containsExactly("DABAEUM_FABRIC");

        assertThat(transactionRepository.findById(poc.id())).get()
            .extracting(BlockchainTransaction::status)
            .isEqualTo(BlockchainTransactionStatus.PENDING);
    }

    @Test
    void networklessIdempotencyLookupRemainsLegacyOnlyAndSingleResult() {
        String idempotencyKey = uniqueCode("request");
        PendingBlockchainRequest legacy = pendingRequest("DABAEUM_FABRIC", idempotencyKey);
        BlockchainTransaction poc = pendingTransaction("FABRIC_POC", idempotencyKey);

        blockchainRequestPort.createAnchor(legacy);
        transactionRepository.insert(poc);

        assertThat(blockchainRequestPort.findByIdempotencyKey(
                "DABAEUM_FABRIC", legacy.idempotencyKey()))
            .contains(legacy);
    }

    private Credential issuedCredential(Fixture fixture, String chainKey) {
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);
        groupRepository.insert(group);
        return new Credential(
            UUID.randomUUID(), group.id(), null, uniqueCode("credential"), 1,
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + fixture.userId(),
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUED, NOW, null,
            "header.payload.signature", VC_HASH, chainKey, "COMPACT_JWS_SHA256_V1",
            NOW, null, null, null, null, NOW, NOW);
    }

    private Credential pendingCredential(Fixture fixture) {
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);
        groupRepository.insert(group);
        return new Credential(
            UUID.randomUUID(), group.id(), null, uniqueCode("credential"), 1,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, null, null, null, null, null, NOW, NOW);
    }

    private BlockchainTransaction pendingTransaction(String network) {
        return pendingTransaction(network, uniqueCode("transaction"));
    }

    private BlockchainTransaction pendingTransaction(String network, String idempotencyKey) {
        return new BlockchainTransaction(
            UUID.randomUUID(), "CREDENTIAL", UUID.randomUUID(), network,
            BlockchainTransactionType.VC_ANCHOR, idempotencyKey, UUID.randomUUID(),
            VC_HASH, null, BlockchainTransactionStatus.PENDING, null, null, NOW, null,
            0, null, null, NOW, NOW);
    }

    private PendingBlockchainRequest pendingRequest(String network, String idempotencyKey) {
        return new PendingBlockchainRequest(
            UUID.randomUUID(), UUID.randomUUID(), "VC_ANCHOR", network,
            idempotencyKey, VC_HASH, NOW);
    }

    private Fixture insertCompletionFixture() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20)
            """, courseId, institutionId, uniqueCode("course"), uniqueCode("course-title"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, userId, OffsetDateTime.now());
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_completions (id, enrollment_id) VALUES (?, ?)",
            completionId, enrollmentId);
        return new Fixture(completionId, userId);
    }

    private record Fixture(UUID completionId, UUID userId) {
    }
}
