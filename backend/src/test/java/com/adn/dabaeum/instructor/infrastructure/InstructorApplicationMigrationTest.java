package com.adn.dabaeum.instructor.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstructorApplicationMigrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V12__create_instructor_applications.sql"
    );

    @Test
    void createsAuditedInstructorApplicationWithPendingUniqueness() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION);
        assertThat(sql)
            .contains("CREATE TABLE tb_instructor_applications")
            .contains("status IN ('PENDING', 'APPROVED', 'REJECTED')")
            .contains("uq_tb_instructor_applications_pending")
            .contains("WHERE status = 'PENDING'")
            .contains("reviewed_by")
            .contains("rejection_reason")
            .doesNotContain("DROP TABLE", "TRUNCATE");
    }
}
