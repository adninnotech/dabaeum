package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 두 raw JDBC 커넥션으로 유출 선언의 {@code FOR UPDATE}(§6.5)가 발급 확정의 {@code FOR SHARE}
 * (§8.1 10단계)와 같은 키 버전 행에서 직렬화됨을 실 PostgreSQL 로 확인한다.
 *
 * <p>시험 트랜잭션 안의 INSERT 는 다른 커넥션에 보이지 않으므로 픽스처와 잠금을 전부 raw
 * 커넥션으로 다룬다. 이 때문에 시험 메서드는 클래스 레벨 {@code @Transactional} 을
 * {@code propagation = NOT_SUPPORTED} 로 무시해, 픽스처 INSERT/DELETE 가 즉시 커밋되게 한다.
 */
class SignKeyVersionLockIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";

    private UUID institutionId;
    private UUID signKeyId;
    private UUID versionId;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 발급_확정의_FOR_SHARE_가_살아_있으면_유출_선언의_FOR_UPDATE_는_기다리고_발급끼리는_막지_않는다() throws SQLException {
        institutionId = UUID.randomUUID();
        signKeyId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        try {
            insertFixture();
            try (Connection a = dataSource.getConnection();
                 Connection b = dataSource.getConnection();
                 Connection c = dataSource.getConnection()) {
                a.setAutoCommit(false);
                b.setAutoCommit(false);
                c.setAutoCommit(false);
                lock(a, versionId, "FOR SHARE");
                try (Statement s = b.createStatement()) {
                    // 트랜잭션 범위(LOCAL)로 걸어야 b.rollback() 뒤 재시도에 영향이 없고,
                    // 커넥션이 풀(HikariPool, size 3)에 반환돼도 세션에 남지 않는다.
                    s.execute("SET LOCAL lock_timeout = '1000'");
                }
                assertThatThrownBy(() -> lock(b, versionId, "FOR UPDATE"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("55P03");
                b.rollback();
                lock(c, versionId, "FOR SHARE");   // 발급끼리는 즉시 성공
                c.rollback();
                a.rollback();
                lock(b, versionId, "FOR UPDATE");  // A 가 놓으면 바로 잡힌다 (LOCAL 설정은 이미 rollback 으로 해제됨)
                b.rollback();
            }
        } finally {
            deleteFixture();
        }
    }

    private static void lock(Connection connection, UUID versionId, String clause) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT status FROM tb_institution_sign_key_versions WHERE id = ? " + clause)) {
            ps.setObject(1, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    private void insertFixture() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)")) {
                ps.setObject(1, institutionId);
                ps.setString(2, uniqueCode("institution"));
                ps.setString(3, uniqueCode("institution-name"));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tb_institution_sign_keys (
                    id, institution_id, purpose, generation, is_current, mount, key_name,
                    current_version, status, version_no, created_at, updated_at
                ) VALUES (?, ?, 'CREDENTIAL', 1, TRUE, ?, ?, 1, 'ACTIVE', 1, ?, ?)
                """)) {
                ps.setObject(1, signKeyId);
                ps.setObject(2, institutionId);
                ps.setString(3, uniqueCode("mount"));
                ps.setString(4, uniqueCode("key-name"));
                ps.setTimestamp(5, Timestamp.from(NOW));
                ps.setTimestamp(6, Timestamp.from(NOW));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tb_institution_sign_key_versions (
                    id, sign_key_id, institution_id, key_version, kid_fragment, public_key_jwk,
                    source, status, activated_at, created_at
                ) VALUES (?, ?, ?, 1, ?, ?::jsonb, 'OPENBAO', 'ACTIVE', ?, ?)
                """)) {
                ps.setObject(1, versionId);
                ps.setObject(2, signKeyId);
                ps.setObject(3, institutionId);
                ps.setString(4, uniqueCode("v"));
                ps.setString(5, JWK);
                ps.setTimestamp(6, Timestamp.from(NOW));
                ps.setTimestamp(7, Timestamp.from(NOW));
                ps.executeUpdate();
            }
        }
    }

    private void deleteFixture() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM tb_institution_sign_key_versions WHERE id = ?")) {
                ps.setObject(1, versionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM tb_institution_sign_keys WHERE id = ?")) {
                ps.setObject(1, signKeyId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM tb_institutions WHERE id = ?")) {
                ps.setObject(1, institutionId);
                ps.executeUpdate();
            }
        }
    }
}
