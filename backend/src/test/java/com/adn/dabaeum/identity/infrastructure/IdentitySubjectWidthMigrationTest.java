package com.adn.dabaeum.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * LOCAL 신원은 provider_subject 에 이메일을 넣는다. 칸(255)이 이메일 상한(320)보다 좁아
 * 256~320자 가입이 DB 오류로 실패하고 409 IDENTITY_CONFLICT 로 잘못 안내됐다.
 * 다대구 가입 토큰의 같은 칸(512)에 맞춰 넓힌다.
 */
class IdentitySubjectWidthMigrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V56__widen_user_identity_provider_subject.sql"
    );

    @Test
    void widensProviderSubjectToFitEmailAndRegistrationTokenSubjects() throws Exception {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION);
        assertThat(sql)
            .contains("ALTER TABLE tb_user_identities")
            .contains("ALTER COLUMN provider_subject TYPE VARCHAR(512)")
            .doesNotContain("CREATE TABLE", "DROP", "TRUNCATE", "UPDATE ", "DELETE ");
    }
}
