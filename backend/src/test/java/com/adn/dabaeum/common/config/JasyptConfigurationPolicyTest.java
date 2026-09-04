package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JasyptConfigurationPolicyTest {

    @Test
    void commonJasyptPasswordSupportsEncryptedLocalSecrets() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String catalog = Files.readString(Path.of("gradle/libs.versions.toml"));
        String commonConfig = Files.readString(
            Path.of("src/main/resources/application.yml")
        );
        String localConfig = Files.readString(
            Path.of("src/main/resources/application-local.yml")
        );

        assertThat(build).contains("libs.jasypt.spring.boot.starter");
        assertThat(catalog)
            .contains("jasypt-spring-boot-starter")
            .contains("4.0.4");
        assertThat(commonConfig)
            .contains("password: ${JASYPT_ENCRYPTOR_PASSWORD}")
            .doesNotContain("JASYPT_ENCRYPTOR_PASSWORD:")
            .doesNotContain("ENC(");
        assertThat(localConfig)
            .doesNotContain("password: ${JASYPT_ENCRYPTOR_PASSWORD:}")
            .contains("password: ${DABAEUM_DB_PASSWORD}");
        assertThat(countOccurrences(localConfig, "ENC("))
            .isZero();
    }

    private int countOccurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }
}
