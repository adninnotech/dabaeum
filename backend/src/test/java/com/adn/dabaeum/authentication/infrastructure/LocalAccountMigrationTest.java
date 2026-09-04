package com.adn.dabaeum.authentication.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalAccountMigrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V11__add_local_account_password_hash.sql"
    );

    @Test
    void addsLocalPasswordHashWithoutChangingExistingTables() throws Exception {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION);
        assertThat(sql)
            .contains("ALTER TABLE tb_user_identities")
            .contains("ADD COLUMN password_hash VARCHAR(100)")
            .contains("provider = 'LOCAL' OR password_hash IS NULL")
            .doesNotContain("CREATE TABLE", "DROP TABLE", "TRUNCATE");
    }

    @Test
    void excludesLegacyLocalIdentitiesWithoutPasswordHashFromLoginLookup()
        throws Exception {
        String mapper = Files.readString(Path.of(
            "src/main/resources/mybatis/mapper/authentication/LocalAccountMapper.xml"
        ));

        assertThat(mapper).contains("password_hash IS NOT NULL");
    }
}
