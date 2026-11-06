package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationOperationConstraintIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Test
    void rejectsDuplicateExternalSourceId() {
        UUID externalSystemId = insertExternalSystem();
        String externalId = uniqueCode("external-id");

        insertExternalMapping(
            externalSystemId,
            UUID.randomUUID(),
            externalId
        );

        assertSqlState("23505", () -> insertExternalMapping(
            externalSystemId,
            UUID.randomUUID(),
            externalId
        ));
    }

    @Test
    void rejectsDuplicateInternalMapping() {
        UUID externalSystemId = insertExternalSystem();
        UUID internalId = UUID.randomUUID();

        insertExternalMapping(
            externalSystemId,
            internalId,
            uniqueCode("external-id")
        );

        assertSqlState("23505", () -> insertExternalMapping(
            externalSystemId,
            internalId,
            uniqueCode("external-id")
        ));
    }

    @Test
    void storesOnlySecretReference() {
        List<String> secretReferenceTables = jdbcTemplate.queryForList("""
            SELECT table_name
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name IN (
                   'tb_external_systems',
                   'tb_webhook_subscriptions'
               )
               AND column_name = 'secret_ref'
            """, String.class);

        Integer secretColumns = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name IN (
                   'tb_external_systems',
                   'tb_webhook_subscriptions'
               )
               AND column_name = 'secret'
            """, Integer.class);

        assertThat(secretReferenceTables).containsExactlyInAnyOrder(
            "tb_external_systems",
            "tb_webhook_subscriptions"
        );
        assertThat(secretColumns).isZero();
    }

    @Test
    void rejectsDuplicateWebhookDelivery() {
        UUID externalSystemId = insertExternalSystem();
        UUID subscriptionId = insertWebhookSubscription(externalSystemId);
        UUID outboxEventId = insertOutboxEvent();

        insertWebhookDelivery(subscriptionId, outboxEventId, 0);

        assertSqlState("23505", () -> insertWebhookDelivery(
            subscriptionId,
            outboxEventId,
            0
        ));
    }

    @Test
    void rejectsDuplicateIdempotencyScopeAndKey() {
        String scope = uniqueCode("scope");
        String idempotencyKey = uniqueCode("idempotency-key");

        insertIdempotencyRequest(scope, idempotencyKey);

        assertSqlState("23505", () -> insertIdempotencyRequest(
            scope,
            idempotencyKey
        ));
    }

    @Test
    void rejectsNegativeOutboxRetryCount() {
        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_outbox_events (
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                retry_count
            ) VALUES (?, 'COURSE', ?, 'COURSE.CREATED', '{}'::jsonb, -1)
            """, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void rejectsNegativeWebhookDeliveryRetryCount() {
        UUID externalSystemId = insertExternalSystem();
        UUID subscriptionId = insertWebhookSubscription(externalSystemId);
        UUID outboxEventId = insertOutboxEvent();

        assertSqlState("23514", () -> insertWebhookDelivery(
            subscriptionId,
            outboxEventId,
            -1
        ));
    }

    @Test
    void insertsAuditLog() {
        UUID auditLogId = UUID.randomUUID();

        int inserted = jdbcTemplate.update("""
            INSERT INTO tb_audit_logs (
                id,
                action,
                resource_type,
                resource_id
            ) VALUES (?, 'CREATE', 'COURSE', ?)
            """, auditLogId, UUID.randomUUID());

        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM tb_audit_logs
             WHERE id = ?
            """, Integer.class, auditLogId);

        assertThat(inserted).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsAllDomainTables() {
        Integer domainTables = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name LIKE 'tb_%'
            """, Integer.class);

        // V13까지의 도메인 테이블 25개 + V14 프론트 지원 테이블 11개
        // + V18 관리자 정정 이력 + V19 Credential 상태목록 + V22 기관 서명키 4개 (V20·V21 은 CHECK 만)
        // + V26 카테고리 마스터 1개 + V29 과정 첨부파일 1개 + V33 약관 동의 이력 1개
        // + V37 공지 첨부파일 1개 + V38 배너 1개 + V39 학번 카운터·가입 토큰 2개
        // + V40 관리자 접속기록 1개 + V41 강좌 분류 1개 − V42 옛 카테고리 1개
        // + V43 배지 정의 1개 + V44 조회 salt·조회 로그·인기 집계 3개
        // + V46 포트폴리오 공유 1개 + V47 콘텐츠 페이지·사이트 메뉴 2개 + V49 배지 변동 이력 1개
        // + V53 공공데이터 강좌·수집 이력 2개 + V54 추천 노출 로그 1개
        // + V55 추천 모델 실행 이력·점수 2개 (V56·V57 은 컬럼만) + V58 NFT 발행 1개
        // + V59 이용 통계 원문·일별 집계·검색어 집계·집계 완료일 4개 + V60 이용 통계 salt 1개
        // + V61 회원 관심 분야 1개 - V62 평생학번 카운터 1개.
        assertThat(domainTables).isEqualTo(69);
    }

    private UUID insertExternalSystem() {
        UUID externalSystemId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO tb_external_systems (
                id,
                system_code,
                system_name,
                system_type,
                secret_ref
            ) VALUES (?, ?, ?, 'DADAEGU', ?)
            """,
            externalSystemId,
            uniqueCode("system-code"),
            uniqueCode("system-name"),
            uniqueCode("secret-ref")
        );

        return externalSystemId;
    }

    private void insertExternalMapping(
        UUID externalSystemId,
        UUID internalId,
        String externalId
    ) {
        jdbcTemplate.update("""
            INSERT INTO tb_external_mappings (
                id,
                external_system_id,
                resource_type,
                internal_id,
                external_id
            ) VALUES (?, ?, 'COURSE', ?, ?)
            """, UUID.randomUUID(), externalSystemId, internalId, externalId);
    }

    private UUID insertWebhookSubscription(UUID externalSystemId) {
        UUID subscriptionId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO tb_webhook_subscriptions (
                id,
                external_system_id,
                callback_url,
                event_types,
                secret_ref
            ) VALUES (?, ?, ?, ARRAY['COURSE.CREATED']::text[], ?)
            """,
            subscriptionId,
            externalSystemId,
            "https://example.test/" + UUID.randomUUID(),
            uniqueCode("webhook-secret-ref")
        );

        return subscriptionId;
    }

    private UUID insertOutboxEvent() {
        UUID outboxEventId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO tb_outbox_events (
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload
            ) VALUES (?, 'COURSE', ?, 'COURSE.CREATED', '{}'::jsonb)
            """, outboxEventId, UUID.randomUUID());

        return outboxEventId;
    }

    private void insertWebhookDelivery(
        UUID subscriptionId,
        UUID outboxEventId,
        int retryCount
    ) {
        jdbcTemplate.update("""
            INSERT INTO tb_webhook_deliveries (
                id,
                subscription_id,
                outbox_event_id,
                event_type,
                retry_count
            ) VALUES (?, ?, ?, 'COURSE.CREATED', ?)
            """, UUID.randomUUID(), subscriptionId, outboxEventId, retryCount);
    }

    private void insertIdempotencyRequest(String scope, String idempotencyKey) {
        jdbcTemplate.update("""
            INSERT INTO tb_idempotency_requests (
                id,
                scope,
                idempotency_key,
                request_hash,
                expires_at
            ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day')
            """,
            UUID.randomUUID(),
            scope,
            idempotencyKey,
            "a".repeat(64)
        );
    }
}
