package com.adn.dabaeum.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/dabaeum_dev",
    "spring.datasource.hikari.maximum-pool-size=3",
    "spring.datasource.hikari.minimum-idle=0",
    "spring.datasource.hikari.connection-timeout=10000"
})
@ActiveProfiles({"local", "integration-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public abstract class RemotePostgresIntegrationTestSupport {

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private Flyway flyway;

    @BeforeAll
    protected void verifyRemoteDatabaseAndMigrate() throws SQLException {
        verifyDatabaseIdentity();
        rejectUnmanagedExistingTables();

        flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("public")
            .schemas("public")
            .cleanDisabled(true)
            .load();

        flyway.migrate();
        flyway.validate();
    }

    private void verifyDatabaseIdentity() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT current_database(), current_schema()"
             )) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("dabaeum_dev");
            assertThat(result.getString(2)).isEqualTo("public");
        }
    }

    private void rejectUnmanagedExistingTables() {
        Integer flywayHistory = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name = 'flyway_schema_history'
            """, Integer.class);

        Integer domainTables = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name LIKE 'tb_%'
            """, Integer.class);

        if (flywayHistory == 0 && domainTables != null && domainTables > 0) {
            throw new IllegalStateException(
                "Unmanaged tb_ tables exist in dabaeum_dev.public"
            );
        }
    }

    protected Flyway flyway() {
        return flyway;
    }

    protected String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected void assertSqlState(
        String expectedSqlState,
        ThrowingCallable operation
    ) {
        assertThatThrownBy(operation).satisfies(throwable -> {
            Throwable current = throwable;
            while (current != null && !(current instanceof SQLException)) {
                current = current.getCause();
            }

            assertThat(current)
                .as("SQLException cause")
                .isInstanceOf(SQLException.class);
            assertThat(((SQLException) current).getSQLState())
                .isEqualTo(expectedSqlState);
        });
    }
}
