package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialFabricConstraintIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Test
    void renamesCredentialIdentifiersForDabaeumFabric() {
        Integer identifierColumns = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = 'tb_credentials'
               AND column_name IN ('issuer_identifier', 'subject_identifier')
            """, Integer.class);

        assertThat(identifierColumns).isEqualTo(2);
    }

    @Test
    void storesCredentialPayloadAsTextToPreserveSignedBytes() {
        String dataType = jdbcTemplate.queryForObject("""
            SELECT data_type
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = 'tb_credentials'
               AND column_name = 'vc_payload'
            """, String.class);

        assertThat(dataType).isEqualTo("text");
    }

    @Test
    void rejectsTerminalCredentialWithoutPayloadAndIdentifiers() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_credentials (
                id, credential_group_id, credential_no, version_no, status,
                valid_from, issued_at, vc_hash
            ) VALUES (?, ?, ?, ?, 'ISSUED', ?, ?, ?)
            """,
            UUID.randomUUID(), groupId, uniqueCode("credential"), 1,
            OffsetDateTime.now(), OffsetDateTime.now(), "a".repeat(64)));
    }

    @Test
    void rejectsExpiredCredentialWithoutIssuedMaterial() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_credentials (
                id, credential_group_id, credential_no, version_no, status
            ) VALUES (?, ?, ?, ?, 'EXPIRED')
            """, UUID.randomUUID(), groupId, uniqueCode("credential"), 1));
    }

    @Test
    void rejectsUppercaseCredentialHash() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_credentials (
                id, credential_group_id, credential_no, version_no, status, vc_hash
            ) VALUES (?, ?, ?, ?, 'PENDING', ?)
            """,
            UUID.randomUUID(), groupId, uniqueCode("credential"), 1, "A".repeat(64)));
    }

    @Test
    void defaultsBlockchainTransactionsToDabaeumFabricAndAllowsReissue() {
        UUID transactionId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_blockchain_transactions (
                id, reference_type, reference_id, transaction_type, idempotency_key, operation_reason
            ) VALUES (?, 'CREDENTIAL', ?, 'VC_REISSUE', ?, 'test reissue')
            """, transactionId, UUID.randomUUID(), uniqueCode("idempotency"));

        String network = jdbcTemplate.queryForObject(
            "SELECT network FROM tb_blockchain_transactions WHERE id = ?",
            String.class,
            transactionId
        );
        assertThat(network).isEqualTo("DABAEUM_FABRIC");
    }

    private UUID insertCompletionFixture() {
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
        return completionId;
    }

    private UUID insertCredentialGroup(UUID completionId) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_credential_groups (id, completion_id) VALUES (?, ?)",
            groupId, completionId);
        return groupId;
    }
}
