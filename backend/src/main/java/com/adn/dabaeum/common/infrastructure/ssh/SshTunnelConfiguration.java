package com.adn.dabaeum.common.infrastructure.ssh;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableConfigurationProperties(SshTunnelProperties.class)
public class SshTunnelConfiguration {

    @Bean
    SshClientFactory sshClientFactory() {
        return new DefaultSshClientFactory();
    }

    @Bean
    @Primary
    SshPortForwardingClient sshPortForwardingClient(SshClientFactory clientFactory) {
        return new ApacheMinaSshPortForwardingClient(clientFactory);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    SshTunnelManager sshTunnelManager(
        SshTunnelProperties properties,
        @Qualifier("sshPortForwardingClient") SshPortForwardingClient client
    ) {
        return new SshTunnelManager(properties, client);
    }
}
