package com.adn.dabaeum.common.infrastructure.database;

import com.adn.dabaeum.common.infrastructure.ssh.SshTunnelManager;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(
        DataSourceProperties properties,
        SshTunnelManager readyTunnel
    ) {
        if (!readyTunnel.isReady()) {
            throw new IllegalStateException("SSH tunnel is not ready");
        }

        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }
}
