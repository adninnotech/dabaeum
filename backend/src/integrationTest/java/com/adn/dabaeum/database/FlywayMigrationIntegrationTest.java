package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlywayMigrationIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Test
    void createsFlywayHistoryAndCurrentDomainTables() {
        Integer history = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name = 'flyway_schema_history'
            """, Integer.class);

        Integer domainTables = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name IN (
                   'tb_institutions',
                   'tb_users',
                   'tb_user_identities',
                   'tb_user_roles',
                   'tb_instructor_applications',
                   'tb_courses',
                   'tb_course_instructors',
                   'tb_course_sessions',
                   'tb_enrollments',
                   'tb_attendance_records',
                   'tb_attendance_adjustments',
                   'tb_attendance_qr_tokens',
                   'tb_completions',
                   'tb_credential_groups',
                   'tb_credentials',
                   'tb_credential_status_lists',
                   'tb_admin_corrections',
                   'tb_learning_badges',
                   'tb_credential_verifications',
                   'tb_blockchain_transactions',
                   'tb_external_systems',
                   'tb_external_mappings',
                   'tb_webhook_subscriptions',
                   'tb_outbox_events',
                   'tb_webhook_deliveries',
                   'tb_idempotency_requests',
                   'tb_audit_logs',
                   'tb_notices',
                   'tb_faqs',
                   'tb_inquiries',
                   'tb_course_reviews',
                   'tb_course_interests',
                   'tb_notifications',
                   'tb_terms_contents',
                   'tb_common_codes',
                   'tb_files',
                   'tb_institution_applications',
                   'tb_password_reset_tokens'
               )
            """, Integer.class);

        Integer totalDomainTables = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name LIKE 'tb_%'
            """, Integer.class);

        assertThat(history).isEqualTo(1);
        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(domainTables).isEqualTo(38);
        assertThat(totalDomainTables).isEqualTo(38);
    }

    @Test
    void preservesAppliedMigrationHistoryThroughLatestVersion() {
        List<String> versions = jdbcTemplate.queryForList("""
            SELECT version
              FROM flyway_schema_history
             WHERE success = true
             ORDER BY installed_rank
            """, String.class);

        assertThat(versions).containsExactly(
            "1", "1.1", "2", "3", "4", "5", "6", "7", "8", "9", "9.1", "9.2", "9.3",
            "10", "10.1", "11", "12", "13", "14", "15", "16", "17", "18", "19"
        );
    }

    @Test
    void aSecondMigrateExecutesNoMigration() {
        assertThat(flyway().migrate().migrationsExecuted).isZero();
    }

    @Test
    void namesV1PartialUniqueIndexesWithTheIxPrefix() {
        List<String> indexNames = jdbcTemplate.queryForList("""
            SELECT indexname
              FROM pg_indexes
             WHERE schemaname = 'public'
               AND indexname IN (
                   'ix_tb_institutions_institution_code_active',
                   'ix_tb_user_identities_external_did',
                   'ix_tb_user_roles_institution_role',
                   'ix_tb_user_roles_platform_role'
               )
            """, String.class);

        assertThat(indexNames).containsExactlyInAnyOrder(
            "ix_tb_institutions_institution_code_active",
            "ix_tb_user_identities_external_did",
            "ix_tb_user_roles_institution_role",
            "ix_tb_user_roles_platform_role"
        );
    }

    @Test
    void usesV4UniqueIndexesForCredentialLookupPaths() {
        List<String> constraintNames = jdbcTemplate.queryForList("""
            SELECT conname
              FROM pg_constraint
             WHERE conrelid = 'public.tb_credentials'::regclass
               AND contype = 'u'
               AND conname IN (
                   'uq_tb_credentials_group_version',
                   'uq_tb_credentials_credential_no'
               )
            """, String.class);

        List<String> indexNames = jdbcTemplate.queryForList("""
            SELECT indexname
              FROM pg_indexes
             WHERE schemaname = 'public'
               AND tablename = 'tb_credentials'
               AND indexname IN (
                   'uq_tb_credentials_group_version',
                   'uq_tb_credentials_credential_no'
               )
            """, String.class);

        List<String> definitions = jdbcTemplate.queryForList("""
            SELECT pg_get_indexdef(conindid)
              FROM pg_constraint
             WHERE conrelid = 'public.tb_credentials'::regclass
               AND contype = 'u'
               AND conname IN (
                   'uq_tb_credentials_group_version',
                   'uq_tb_credentials_credential_no'
               )
            """, String.class);

        assertThat(constraintNames).containsExactlyInAnyOrder(
            "uq_tb_credentials_group_version",
            "uq_tb_credentials_credential_no"
        );
        assertThat(indexNames).containsExactlyInAnyOrder(
            "uq_tb_credentials_group_version",
            "uq_tb_credentials_credential_no"
        );
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("(credential_group_id, version_no)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("(credential_no)"));
    }

    @Test
    void addsV8QrTokenPairingConstraintsAndAttendanceAdjustmentIndex() {
        List<String> constraintNames = jdbcTemplate.queryForList("""
            SELECT conname
              FROM pg_constraint
             WHERE conrelid IN (
                 'public.tb_attendance_qr_tokens'::regclass,
                 'public.tb_attendance_records'::regclass
             )
               AND conname IN (
                   'uq_tb_attendance_qr_tokens_token_hash',
                   'fk_tb_attendance_records_qr_token_id',
                   'ck_tb_attendance_records_qr_token_pair'
               )
            """, String.class);

        List<String> indexNames = jdbcTemplate.queryForList("""
            SELECT indexname
              FROM pg_indexes
             WHERE schemaname = 'public'
               AND indexname IN (
                   'uq_tb_attendance_qr_tokens_unrevoked_session',
                   'ix_tb_attendance_adjustments_record_adjusted'
               )
            """, String.class);

        assertThat(constraintNames).containsExactlyInAnyOrder(
            "uq_tb_attendance_qr_tokens_token_hash",
            "fk_tb_attendance_records_qr_token_id",
            "ck_tb_attendance_records_qr_token_pair"
        );
        assertThat(indexNames).containsExactlyInAnyOrder(
            "uq_tb_attendance_qr_tokens_unrevoked_session",
            "ix_tb_attendance_adjustments_record_adjusted"
        );
    }

    @Test
    void hasNoExistingQrAttendanceRowsBeforeV8Migration() {
        Integer qrRows = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM tb_attendance_records
             WHERE attendance_method = 'QR'
            """, Integer.class);

        assertThat(qrRows).isZero();
    }

    @Test
    void preservesTwelveV6GeneralIndexPathsAfterV7Corrections() {
        List<String> definitions = jdbcTemplate.queryForList("""
            SELECT indexname || ':' || indexdef
              FROM pg_indexes
             WHERE schemaname = 'public'
               AND indexname IN (
                   'ix_tb_courses__institution_status',
                   'ix_tb_course_sessions__course_starts_at',
                   'ix_tb_enrollments__user_status',
                   'ix_tb_attendance_records__enrollment',
                   'ix_tb_completions__status',
                   'ix_tb_credential_verifications__credential_verified_at',
                   'ix_tb_blockchain_transactions__status_next_retry',
                   'ix_tb_external_mappings__internal',
                   'ix_tb_outbox_events__status_next_retry',
                   'ix_tb_webhook_deliveries__status_next_retry',
                   'ix_tb_idempotency_requests__expires_at',
                   'ix_tb_audit_logs__resource_created_at'
               )
            """, String.class);

        assertThat(definitions).hasSize(12);
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_courses__institution_status")
            .contains("(institution_id, status)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_course_sessions__course_starts_at")
            .contains("(course_id, starts_at)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_enrollments__user_status")
            .contains("(user_id, status)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_attendance_records__enrollment")
            .contains("(enrollment_id)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_completions__status")
            .contains("(status)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_credential_verifications__credential_verified_at")
            .contains("(credential_id, verified_at DESC)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_blockchain_transactions__status_next_retry")
            .contains("(status, next_retry_at)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_external_mappings__internal")
            .contains("(resource_type, internal_id)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_outbox_events__status_next_retry")
            .contains("(status, next_retry_at)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_webhook_deliveries__status_next_retry")
            .contains("(status, next_retry_at)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_idempotency_requests__expires_at")
            .contains("(expires_at)"));
        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
            .contains("ix_tb_audit_logs__resource_created_at")
            .contains("(resource_type, resource_id, created_at)"));
    }
}
