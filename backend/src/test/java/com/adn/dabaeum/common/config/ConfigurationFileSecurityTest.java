package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

class ConfigurationFileSecurityTest {

    @Test
    void mainConfigurationMustNotSelectTestAsDefaultProfile() throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(common)
            .doesNotContain("profiles:\n    default: test")
            .doesNotContain("dabaeum_prd");
    }

    @Test
    void localConfigurationMustUseTunnelAndEnvironmentSecrets() throws Exception {
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(local)
            .contains("jdbc:postgresql://127.0.0.1:15432/dabaeum_dev")
            .containsPattern("bearer-token: \\$\\{DABAEUM_DEV_BEARER_TOKEN:[^}]+\\}")
            .contains("password: ${DABAEUM_DB_PASSWORD}")
            .contains("${DABAEUM_SSH_KNOWN_HOSTS:${user.home}/.ssh/known_hosts}")
            .doesNotContain("password: postgres")
            .doesNotContain("dabaeum_prd");
    }

    // dev 비밀은 서버 env 파일에서만 받는다. 기본값을 두지 않아 빠지면 기동이 실패한다.
    @Test
    void devSecretsMustComeFromEnvironmentWithoutDefault() throws Exception {
        String dev = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(dev)
            .contains("username: ${DABAEUM_DB_USERNAME}")
            .contains("password: ${DABAEUM_DB_PASSWORD}")
            .contains("key-id: ${DABAEUM_JWT_KEY_ID}")
            .contains("signing-key: ${DABAEUM_JWT_SIGNING_KEY}")
            .contains("site-id: ${DABAEUM_DADAEGU_SITE_ID}")
            .contains("private-key: ${DABAEUM_DADAEGU_PRIVATE_KEY}");
    }

    // local JWT 키와 다대구 값은 공통 파일에 두지 않는다. 공통에 있으면 dev 도 그 값을 물려받는다.
    @Test
    void commonConfigurationMustNotHoldEnvironmentSecrets() throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(common)
            .doesNotContain("signing-key:")
            .doesNotContain("local-development-1")
            .doesNotContain("site-id:")
            .doesNotContain("private-key:");
        assertThat(local).contains("key-id: local-1");
    }

    @Test
    void devConfigurationMustNotEnableFixedBearerToken() throws Exception {
        String dev = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(dev)
            .doesNotContain("bearer-token")
            .doesNotContain("DABAEUM_DEV_BEARER_TOKEN");
    }

    @Test
    void devHttpLogMustDisablePayloadsAndEnableMasking() throws Exception {
        var source = new YamlPropertySourceLoader().load("dev",
            new FileSystemResource("src/main/resources/application-dev.yml")).getFirst();

        assertThat(source.getProperty("dabaeum.http-logging.body-enabled")).isEqualTo(false);
        assertThat(source.getProperty("dabaeum.http-logging.mask-sensitive")).isEqualTo(true);
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
