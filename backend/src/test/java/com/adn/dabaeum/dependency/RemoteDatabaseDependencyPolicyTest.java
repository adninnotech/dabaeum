package com.adn.dabaeum.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RemoteDatabaseDependencyPolicyTest {

    @Test
    void projectDoesNotContainContainerBasedDatabaseTestingDependencies()
        throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String catalog = Files.readString(Path.of("gradle/libs.versions.toml"));
        String compatibilityTest = Files.readString(Path.of(
            "src/test/java/com/adn/dabaeum/dependency/DependencyCompatibilityTest.java"
        ));

        assertThat(build)
            .doesNotContain("org.testcontainers")
            .doesNotContain("libs.testcontainers");
        assertThat(catalog).doesNotContain("testcontainers");
        assertThat(compatibilityTest).doesNotContain("PostgreSQLContainer");
    }
}
