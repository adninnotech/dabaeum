package com.adn.dabaeum.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * local 프로필은 프로젝트 루트 local-secrets.env 의 값이 있어야 뜬다. 값이 빠졌을 때 placeholder 오류 대신
 * 빠진 이름과 설정 방법을 바로 알려준다.
 *
 * <p>테스트 컨텍스트에서는 돌지 않도록 {@code DabaeumApplication.main} 에서만 붙인다.
 */
public class LocalSecretsPresenceCheck
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    // 정본은 local-secrets.env.example 이다. SecretConfigurationPolicyTest 가 둘이 같은지 확인한다.
    static final List<String> REQUIRED_NAMES = List.of(
        "DABAEUM_SSH_USERNAME",
        "DABAEUM_SSH_PASSWORD",
        "DABAEUM_DB_USERNAME",
        "DABAEUM_DB_PASSWORD",
        "DABAEUM_JWT_SIGNING_KEY",
        "DABAEUM_DADAEGU_SITE_ID",
        "DABAEUM_DADAEGU_PRIVATE_KEY");

    private final Path secretsFile;

    public LocalSecretsPresenceCheck() {
        this(Path.of("local-secrets.env"));
    }

    LocalSecretsPresenceCheck(Path secretsFile) {
        this.secretsFile = secretsFile;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        check(event.getEnvironment());
    }

    void check(ConfigurableEnvironment environment) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            return;
        }
        // spring.config.import 는 실행 폴더 기준이다. 다른 폴더에서 띄우면 파일을 만들어 두고도 못 찾는다.
        if (Files.notExists(secretsFile)) {
            throw new IllegalStateException("local-secrets.env 를 찾지 못했습니다: "
                + secretsFile.toAbsolutePath()
                + " (앱을 실행한 폴더 기준). 프로젝트 루트에서 실행하거나,"
                + " 없으면 cp local-secrets.env.example local-secrets.env 후 값을 채우세요.");
        }
        List<String> missing = REQUIRED_NAMES.stream()
            .filter(name -> {
                String value = environment.getProperty(name);
                return value == null || value.isBlank();
            })
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("local 프로필에 필요한 값이 없습니다: " + missing
                + ". 프로젝트 루트에서 cp local-secrets.env.example local-secrets.env 후 값을 채우세요."
                + " 자세한 것은 scripts/README.md 의 '로컬 실행' 절을 보세요.");
        }
    }
}
