package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpSqlLoggingConfigurationTest {

    private static final List<String> MYBATIS_PACKAGES = List.of(
        "attendance",
        "completion",
        "course",
        "enrollment",
        "identity",
        "institution",
        "role",
        "user"
    );

    @Test
    void enablesSafeHttpAndSqlLoggingForLocalAndDevProfiles() throws Exception {
        for (String profile : List.of("local", "dev")) {
            String configuration = Files.readString(
                Path.of("src/main/resources/application-" + profile + ".yml")
            );

            assertThat(configuration)
                .contains(
                    "log-impl: com.adn.dabaeum.common.logging.SanitizedMyBatisLog"
                )
                .contains(
                    "com.adn.dabaeum.common.web.HttpTrafficLoggingFilter: INFO"
                );
            for (String packageName : MYBATIS_PACKAGES) {
                assertThat(configuration)
                    .contains(
                        "com.adn.dabaeum."
                            + packageName
                            + ".infrastructure.mybatis: DEBUG"
                    );
            }
        }
    }
}
