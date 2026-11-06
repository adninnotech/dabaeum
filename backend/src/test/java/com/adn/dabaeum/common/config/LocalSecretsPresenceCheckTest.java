package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/**
 * local 프로필은 local-secrets.env 의 값이 있어야 뜬다. 값이 빠졌을 때 placeholder 오류 대신 빠진 이름과
 * 설정 방법을 바로 알려준다.
 */
class LocalSecretsPresenceCheckTest {

    @TempDir
    Path directory;

    @Test
    void local_프로필에서_값이_빠지면_이름과_설정_방법을_알려주며_멈춘다() throws Exception {
        MockEnvironment environment = completeLocalEnvironment()
            .withProperty("DABAEUM_DB_PASSWORD", "")
            .withProperty("DABAEUM_JWT_SIGNING_KEY", " ");

        assertThatThrownBy(() -> checkWithExistingFile().check(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DABAEUM_DB_PASSWORD")
            .hasMessageContaining("DABAEUM_JWT_SIGNING_KEY")
            .hasMessageContaining("local-secrets.env.example")
            .hasMessageNotContaining("DABAEUM_SSH_USERNAME");
    }

    // 다른 폴더에서 실행하면 파일을 못 찾는다. 파일을 만들었는데도 헷갈리지 않게 찾은 위치를 알려준다.
    @Test
    void 파일이_없으면_실행_위치_기준의_찾은_경로를_알려준다() {
        Path missing = directory.resolve("local-secrets.env");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatThrownBy(() -> new LocalSecretsPresenceCheck(missing).check(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(missing.toAbsolutePath().toString())
            .hasMessageContaining("실행한 폴더");
    }

    @Test
    void local_프로필에서_값이_모두_있으면_통과한다() throws Exception {
        assertThatCode(() -> checkWithExistingFile().check(completeLocalEnvironment()))
            .doesNotThrowAnyException();
    }

    @Test
    void dev_프로필은_검사하지_않는다() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThatCode(() -> new LocalSecretsPresenceCheck(directory.resolve("none")).check(environment))
            .doesNotThrowAnyException();
    }

    private LocalSecretsPresenceCheck checkWithExistingFile() throws Exception {
        return new LocalSecretsPresenceCheck(
            Files.createFile(directory.resolve("local-secrets.env")));
    }

    private MockEnvironment completeLocalEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        LocalSecretsPresenceCheck.REQUIRED_NAMES.forEach(name -> environment.setProperty(name, "v"));
        environment.setActiveProfiles("local");
        return environment;
    }
}
