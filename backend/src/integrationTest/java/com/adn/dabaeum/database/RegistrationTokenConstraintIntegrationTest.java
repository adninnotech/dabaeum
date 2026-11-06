package com.adn.dabaeum.database;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** V39 — 다대구 가입 토큰 제약 (설계서 §4.2). 제약 위반은 한 테스트에 하나씩만 둔다(25P02). */
class RegistrationTokenConstraintIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Test
    void registrationTokenHashIsUnique() {
        insertRegistrationToken("a".repeat(64));

        assertSqlState("23505", () -> insertRegistrationToken("a".repeat(64)));
    }

    private void insertRegistrationToken(String hash) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-28T00:00:00Z");
        jdbcTemplate.update("""
            INSERT INTO tb_registration_tokens (
                id, token_hash, provider, provider_subject, external_did, name,
                expires_at, created_at
            ) VALUES (?, ?, 'DADAEGU', ?, ?, '홍길동', ?, ?)
            """, UUID.randomUUID(), hash, "did:test:" + UUID.randomUUID(),
            "did:test:" + UUID.randomUUID(), now.plusMinutes(10), now);
    }
}
