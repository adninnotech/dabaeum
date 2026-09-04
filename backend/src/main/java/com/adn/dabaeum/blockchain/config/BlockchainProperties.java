package com.adn.dabaeum.blockchain.config;

import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 원장 Provider 선택과 실제 쓰기 승인 상태를 바인딩한다. */
@ConfigurationProperties(prefix = "dabaeum.blockchain")
public record BlockchainProperties(String provider, boolean writeEnabled) {

    public BlockchainProperties {
        provider = provider == null || provider.isBlank() ? "fake" : provider;
        // fabric-poc 는 Fabric SDK 연동을 걷어내며 사라졌다 (ADR-0005). 원장은 daeguchain 으로만 붙는다.
        if (!"fake".equals(provider) && !"daeguchain".equals(provider)) {
            throw new IllegalArgumentException("blockchain provider is not supported");
        }
    }

    /** 업무 코드가 원장 참조와 트랜잭션 network 를 만들 때 쓰는 대상. fake 는 FABRIC_POC 로 본다. */
    public RegistryTarget target() {
        return "daeguchain".equals(provider) ? RegistryTarget.DAEGUCHAIN : RegistryTarget.FABRIC_POC;
    }
}
