package com.adn.dabaeum.common.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.infrastructure.ssh.SshTunnelConfiguration;
import com.adn.dabaeum.common.infrastructure.ssh.SshTunnelManager;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalDataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            SshTunnelConfiguration.class,
            LocalDataSourceConfiguration.class
        );

    @Test
    void testProfileDoesNotCreateSshTunnelOrDatasource() {
        contextRunner
            .withPropertyValues("spring.profiles.active=test")
            .run(context -> {
                assertThat(context).doesNotHaveBean(SshTunnelManager.class);
                assertThat(context).doesNotHaveBean(HikariDataSource.class);
            });
    }

    @Test
    void integrationTestProfileDoesNotCreateSshTunnelOrDatasource() {
        contextRunner
            .withPropertyValues("spring.profiles.active=integration-test")
            .run(context -> {
                assertThat(context).doesNotHaveBean(SshTunnelManager.class);
                assertThat(context).doesNotHaveBean(HikariDataSource.class);
            });
    }
}
