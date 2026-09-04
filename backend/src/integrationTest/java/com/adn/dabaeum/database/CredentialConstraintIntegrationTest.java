package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialConstraintIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Test
    void rejectsSecondGroupForCompletion() {
        UUID completionId = insertCompletionFixture();

        insertCredentialGroup(completionId);

        assertSqlState("23505", () -> insertCredentialGroup(completionId));
    }

    @Test
    void rejectsDuplicateCredentialVersion() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        insertCredential(groupId, 1, "PENDING", null);

        assertSqlState("23505", () -> insertCredential(groupId, 1, "PENDING", null));
    }

    @Test
    void rejectsSecondIssuedCredential() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        insertCredential(groupId, 1, "ISSUED", null);

        assertSqlState("23505", () -> insertCredential(groupId, 2, "ISSUED", null));
    }

    @Test
    void rejectsIssuedWithoutRequiredFields() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        assertSqlState("23514", () -> jdbcTemplate.update(
            """
                INSERT INTO tb_credentials (id, credential_group_id, credential_no, version_no, status)
                VALUES (?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            groupId,
            uniqueCode("credential"),
            1,
            "ISSUED"
        ));
    }

    @Test
    void rejectsFailedWithoutFailureCode() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        assertSqlState("23514", () -> insertCredential(groupId, 1, "FAILED", null));
    }

    @Test
    void rejectsRevokedWithoutReasonAndTime() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());

        assertSqlState("23514", () -> insertCredential(groupId, 1, "REVOKED", null));
    }

    @Test
    void acceptsNotFoundWithoutCredentialId() {
        UUID verificationId = UUID.randomUUID();

        assertThat(jdbcTemplate.update(
            """
                INSERT INTO tb_credential_verifications (
                    id, presented_credential_no, verification_type, requester_type, result
                ) VALUES (?, ?, ?, ?, ?)
                """,
            verificationId,
            uniqueCode("presented-credential"),
            "QR",
            "INDIVIDUAL",
            "NOT_FOUND"
        )).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_credential_verifications WHERE id = ?",
            Integer.class,
            verificationId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsValidWithoutCredentialId() {
        assertSqlState("23514", () -> jdbcTemplate.update(
            """
                INSERT INTO tb_credential_verifications (
                    id, presented_credential_no, verification_type, requester_type, result
                ) VALUES (?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            uniqueCode("presented-credential"),
            "API",
            "EXTERNAL_ORGANIZATION",
            "VALID"
        ));
    }

    @Test
    void rejectsVerificationWithoutIdentifier() {
        assertSqlState("23514", () -> jdbcTemplate.update(
            """
                INSERT INTO tb_credential_verifications (
                    id, verification_type, requester_type, result
                ) VALUES (?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            "ADMIN",
            "SYSTEM",
            "NOT_FOUND"
        ));
    }

    @Test
    void allowsReissueAfterSupersedingPreviousCredential() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());
        UUID previousCredentialId = insertCredential(groupId, 1, "ISSUED", null);

        assertThat(jdbcTemplate.update(
            "UPDATE tb_credentials SET status = 'SUPERSEDED' WHERE id = ?",
            previousCredentialId
        )).isEqualTo(1);
        UUID currentCredentialId = UUID.randomUUID();
        assertThat(insertCredential(currentCredentialId, groupId, 2, "ISSUED", previousCredentialId))
            .isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_credentials WHERE credential_group_id = ? AND status = 'ISSUED'",
            Integer.class,
            groupId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsReverseReissueOrder() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());
        UUID previousCredentialId = insertCredential(groupId, 1, "ISSUED", null);

        assertSqlState("23505", () ->
            insertCredential(groupId, 2, "ISSUED", previousCredentialId)
        );
    }

    @Test
    void rejectsPreviousCredentialFromDifferentGroup() {
        UUID firstGroupId = insertCredentialGroup(insertCompletionFixture());
        UUID secondGroupId = insertCredentialGroup(insertCompletionFixture());
        UUID previousCredentialId = insertCredential(firstGroupId, 1, "PENDING", null);

        assertSqlState("23503", () ->
            insertCredential(secondGroupId, 1, "PENDING", previousCredentialId)
        );
    }

    @Test
    void rejectsCredentialAsItsOwnPreviousCredential() {
        UUID groupId = insertCredentialGroup(insertCompletionFixture());
        UUID credentialId = UUID.randomUUID();

        assertSqlState("23514", () ->
            insertCredential(credentialId, groupId, 1, "PENDING", credentialId)
        );
    }

    @Test
    void rejectsDuplicateBlockchainTransactionId() {
        String transactionId = uniqueCode("transaction");
        insertBlockchainTransaction("DAEGUCHAIN", uniqueCode("idempotency"), transactionId);

        assertSqlState("23505", () ->
            insertBlockchainTransaction("DAEGUCHAIN", uniqueCode("idempotency"), transactionId)
        );
    }

    @Test
    void rejectsDuplicateBlockchainIdempotencyKey() {
        String idempotencyKey = uniqueCode("idempotency");
        insertBlockchainTransaction("DAEGUCHAIN", idempotencyKey, uniqueCode("transaction"));

        assertSqlState("23505", () ->
            insertBlockchainTransaction("DAEGUCHAIN", idempotencyKey, uniqueCode("transaction"))
        );
    }

    private UUID insertCompletionFixture() {
        UUID institutionId = insertInstitution();
        UUID userId = insertUser();
        UUID courseId = insertCourse(institutionId);
        UUID enrollmentId = insertEnrollment(courseId, userId);
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_completions (id, enrollment_id) VALUES (?, ?)",
            completionId,
            enrollmentId
        );
        return completionId;
    }

    private UUID insertCredentialGroup(UUID completionId) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_credential_groups (id, completion_id) VALUES (?, ?)",
            groupId,
            completionId
        );
        return groupId;
    }

    private UUID insertCredential(UUID groupId, int versionNo, String status, UUID previousCredentialId) {
        UUID credentialId = UUID.randomUUID();
        insertCredential(credentialId, groupId, versionNo, status, previousCredentialId);
        return credentialId;
    }

    private int insertCredential(
        UUID credentialId,
        UUID groupId,
        int versionNo,
        String status,
        UUID previousCredentialId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.update(
            """
                INSERT INTO tb_credentials (
                    id, credential_group_id, previous_credential_id, credential_no, version_no,
                    status, issuer_identifier, subject_identifier, vc_payload,
                    issued_at, valid_from, vc_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            credentialId,
            groupId,
            previousCredentialId,
            uniqueCode("credential"),
            versionNo,
            status,
            "ISSUED".equals(status) ? "urn:dabaeum:institution:test" : null,
            "ISSUED".equals(status) ? "urn:dabaeum:user:test" : null,
            "ISSUED".equals(status) ? "{\"credentialSubject\":{}}" : null,
            "ISSUED".equals(status) ? now : null,
            "ISSUED".equals(status) ? now : null,
            "ISSUED".equals(status) ? "a".repeat(64) : null
        );
    }

    private int insertBlockchainTransaction(
        String network,
        String idempotencyKey,
        String transactionId
    ) {
        return jdbcTemplate.update(
            """
                INSERT INTO tb_blockchain_transactions (
                    id, reference_type, reference_id, network, transaction_type,
                    idempotency_key, transaction_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            "CREDENTIAL",
            UUID.randomUUID(),
            network,
            "VC_ANCHOR",
            idempotencyKey,
            transactionId
        );
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId,
            uniqueCode("institution"),
            uniqueCode("institution-name")
        );
        return institutionId;
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId,
            userCode,
            userCode + "@example.test"
        );
        return userId;
    }

    private UUID insertCourse(UUID institutionId) {
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type,
                    start_date, end_date, capacity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            courseId,
            institutionId,
            uniqueCode("course"),
            uniqueCode("course-title"),
            "OFFLINE",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            20
        );
        return courseId;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, ?)",
            enrollmentId,
            courseId,
            userId,
            "APPLIED"
        );
        return enrollmentId;
    }
}
