package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigurationFileSecurityTest {

    @Test
    void mainConfigurationMustNotSelectTestAsDefaultProfile() throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(common)
            .doesNotContain("profiles:\n    default: test")
            .doesNotContain("dabaeum_prd");
    }

    @Test
    void localConfigurationMustUseTunnelAndInjectedSecrets() throws Exception {
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(local)
            .contains("jdbc:postgresql://127.0.0.1:15432/dabaeum_dev")
            .doesNotContain("password: ${JASYPT_ENCRYPTOR_PASSWORD:}")
            .contains("bearer-token: ${DABAEUM_DEV_BEARER_TOKEN}")
            .contains("password: ${DABAEUM_DB_PASSWORD}")
            .contains("password: ${DABAEUM_SSH_PASSWORD}")
            .doesNotContain("ENC(")
            .contains("${DABAEUM_SSH_KNOWN_HOSTS:${user.home}/.ssh/known_hosts}")
            .doesNotContain("password: postgres")
            .doesNotContain("dabaeum_prd");
    }

    @Test
    void applicationConfigurationIsManagedByExactlyThreeYamlFiles() throws Exception {
        try (var paths = Files.walk(Path.of("src"))) {
            assertThat(paths
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.startsWith("application-") || name.equals("application.yml"))
                .filter(name -> name.endsWith(".yml"))
                .sorted()
                .toList())
                .containsExactly("application-dev.yml", "application-local.yml", "application.yml");
        }
    }
}
