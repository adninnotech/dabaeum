package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 비밀은 저장소에 두지 않고 환경변수로만 받는다. local 은 프로젝트 루트의 local-secrets.env(커밋 안 함),
 * dev 는 서버 env 파일에서 같은 이름으로 받는다. 암호화한 값(ENC)도 두지 않는다.
 */
class SecretConfigurationPolicyTest {

    private static final List<String> LOCAL_SECRET_NAMES = List.of(
        "DABAEUM_SSH_USERNAME",
        "DABAEUM_SSH_PASSWORD",
        "DABAEUM_DB_USERNAME",
        "DABAEUM_DB_PASSWORD",
        "DABAEUM_JWT_SIGNING_KEY",
        "DABAEUM_DADAEGU_SITE_ID",
        "DABAEUM_DADAEGU_PRIVATE_KEY");

    @Test
    void buildMustNotDependOnJasypt() throws Exception {
        assertThat(Files.readString(Path.of("build.gradle"))).doesNotContainIgnoringCase("jasypt");
        assertThat(Files.readString(Path.of("gradle/libs.versions.toml")))
            .doesNotContainIgnoringCase("jasypt");
    }

    @Test
    void configurationFilesMustNotHoldEncryptedOrLiteralSecrets() throws Exception {
        for (String name : List.of("application.yml", "application-dev.yml", "application-local.yml")) {
            String yaml = Files.readString(Path.of("src/main/resources", name));
            assertThat(yaml).as(name)
                .doesNotContain("ENC(")
                .doesNotContainIgnoringCase("jasypt");
        }
    }

    @Test
    void localConfigurationMustReadSecretsFromLocalSecretsFile() throws Exception {
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(local)
            .contains("optional:file:local-secrets.env[.properties]")
            .contains("username: ${DABAEUM_SSH_USERNAME}")
            .contains("password: ${DABAEUM_SSH_PASSWORD}")
            .contains("username: ${DABAEUM_DB_USERNAME}")
            .contains("password: ${DABAEUM_DB_PASSWORD}")
            .contains("key-id: local-1")
            .contains("signing-key: ${DABAEUM_JWT_SIGNING_KEY}")
            .contains("site-id: ${DABAEUM_DADAEGU_SITE_ID}")
            .contains("private-key: ${DABAEUM_DADAEGU_PRIVATE_KEY}");
    }

    // 이름 목록은 서식 파일이 정본이다. 기동 안내 가드와 통합 테스트 스크립트가 같은 이름을 쓰는지 강제한다.
    @Test
    void localSecretsExampleListsNamesWithoutValues() throws Exception {
        List<String> assignments = Files.readAllLines(Path.of("local-secrets.env.example")).stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();
        List<String> names = assignments.stream().map(line -> line.substring(0, line.indexOf('='))).toList();

        assertThat(assignments).allSatisfy(line -> assertThat(line).endsWith("="));
        assertThat(names).containsExactlyInAnyOrderElementsOf(LOCAL_SECRET_NAMES);
        assertThat(LocalSecretsPresenceCheck.REQUIRED_NAMES)
            .containsExactlyInAnyOrderElementsOf(LOCAL_SECRET_NAMES);
        String integrationScript = Files.readString(Path.of("scripts/run-integration-test.sh"));
        assertThat(LOCAL_SECRET_NAMES).allSatisfy(name -> assertThat(integrationScript).contains(name));
    }

    @Test
    void secretFilesMustBeIgnoredByGit() throws Exception {
        assertThat(Files.readAllLines(Path.of(".gitignore"))).contains("*.env");
    }
}
