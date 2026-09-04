package com.adn.dabaeum.blockchain.config;

import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.blockchain.provider.FakeBlockchainRegistryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 원장 Provider 조립의 공통 부분. 기본은 메모리 Fake 이고, 실제 원장은
 * {@code dabaeum.blockchain.provider=daeguchain} 으로 {@code DaeguChainConfiguration} 이 조립한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BlockchainProperties.class)
public class BlockchainProviderConfiguration {

    /** 현재 구성의 원장 대상. Provider 와 무관하게 항상 하나 있다. */
    @Bean
    RegistryTarget registryTarget(BlockchainProperties properties) {
        return properties.target();
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "dabaeum.blockchain",
        name = "provider",
        havingValue = "fake",
        matchIfMissing = true
    )
    BlockchainRegistryPort fakeBlockchainRegistryPort() {
        return new FakeBlockchainRegistryPort();
    }
}
