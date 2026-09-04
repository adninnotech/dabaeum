package com.adn.dabaeum.credential.config;

import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.application.DefaultCredentialUriProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519PublicJwkFactory;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VcProperties.class)
public class VcConfiguration {

    @Bean
    CredentialUriProvider credentialUriProvider(VcProperties properties) {
        return new DefaultCredentialUriProvider(properties);
    }

    @Bean
    CredentialPublicJwkFactory ed25519PublicJwkFactory() {
        return new Ed25519PublicJwkFactory();
    }
}
