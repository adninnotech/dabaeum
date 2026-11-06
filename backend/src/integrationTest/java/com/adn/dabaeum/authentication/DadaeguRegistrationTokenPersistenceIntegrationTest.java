package com.adn.dabaeum.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.authentication.domain.DadaeguRegistrationToken;
import com.adn.dabaeum.authentication.domain.DadaeguRegistrationTokenRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DadaeguRegistrationTokenPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired DadaeguRegistrationTokenRepository repository;

    @Test
    void consumesAnUnusedTokenExactlyOnce() {
        DadaeguRegistrationToken token = token(hash(), NOW.plusSeconds(600));
        repository.save(token);

        DadaeguRegistrationToken consumed = repository.consume(token.tokenHash(), NOW).orElseThrow();

        assertThat(consumed.did()).isEqualTo(token.did());
        assertThat(consumed.name()).isEqualTo("홍길동");
        assertThat(consumed.phone()).isEqualTo("01012345678");
        assertThat(consumed.birthDate()).isEqualTo(LocalDate.of(1995, 12, 5));
        assertThat(consumed.usedAt()).isEqualTo(NOW);
        assertThat(repository.consume(token.tokenHash(), NOW)).isEmpty();
    }

    @Test
    void expiredOrUnknownTokensAreNotConsumed() {
        DadaeguRegistrationToken expired = token(hash(), NOW.minusSeconds(1));
        repository.save(expired);

        assertThat(repository.consume(expired.tokenHash(), NOW)).isEmpty();
        assertThat(repository.consume(hash(), NOW)).isEmpty();
    }

    @Test
    void deletesOnlyTokensExpiredBeforeThreshold() {
        DadaeguRegistrationToken old = token(hash(), NOW.minusSeconds(2 * 86_400));
        DadaeguRegistrationToken recent = token(hash(), NOW.minusSeconds(3_600));
        DadaeguRegistrationToken alive = token(hash(), NOW.plusSeconds(600));
        repository.save(old);
        repository.save(recent);
        repository.save(alive);

        // dev DB 의 다른 만료 토큰도 같이 지워질 수 있으므로 개수는 단정하지 않는다(트랜잭션 롤백으로 원복된다).
        assertThat(repository.deleteExpiredBefore(NOW.minusSeconds(86_400))).isPositive();

        assertThat(countByHash(old.tokenHash())).isZero();
        assertThat(countByHash(recent.tokenHash())).isEqualTo(1);
        assertThat(countByHash(alive.tokenHash())).isEqualTo(1);
    }

    private int countByHash(String hash) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_registration_tokens WHERE token_hash = ?", Integer.class, hash);
    }

    private static DadaeguRegistrationToken token(String hash, Instant expiresAt) {
        return new DadaeguRegistrationToken(
            UUID.randomUUID(), hash, "did:test:" + UUID.randomUUID(), "홍길동",
            "01012345678", LocalDate.of(1995, 12, 5), expiresAt, null, NOW.minusSeconds(60));
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
