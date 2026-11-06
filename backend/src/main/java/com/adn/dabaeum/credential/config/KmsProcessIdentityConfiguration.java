package com.adn.dabaeum.credential.config;

import com.adn.dabaeum.credential.application.KmsProcessIdentity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 기동마다 새 소유권 식별자를 만든다 (설계서 §11.4). */
@Configuration(proxyBeanMethods = false)
public class KmsProcessIdentityConfiguration {

    @Bean
    KmsProcessIdentity kmsProcessIdentity() {
        return KmsProcessIdentity.generate();
    }
}
