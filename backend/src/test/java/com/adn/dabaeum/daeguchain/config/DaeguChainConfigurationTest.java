package com.adn.dabaeum.daeguchain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.blockchain.config.BlockchainProviderConfiguration;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.provider.DaeguChainStorageProvider;
import com.adn.dabaeum.fabric.application.CredentialFabricWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class DaeguChainConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(BlockchainProviderConfiguration.class, DaeguChainConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void daeguchainProviderAssemblesTheRestProviderAndTargetWithoutAnyFabricBean() {
        context.withPropertyValues(
                "dabaeum.blockchain.provider=daeguchain",
                "dabaeum.daeguchain.base-url=http://127.0.0.1:8090",
                "dabaeum.daeguchain.project-id=EVDCFTOIGQNUVJZDSYAP")
            .run(result -> {
                assertThat(result).hasSingleBean(BlockchainRegistryPort.class);
                assertThat(result.getBean(BlockchainRegistryPort.class))
                    .isInstanceOf(DaeguChainStorageProvider.class);
                assertThat(result).hasSingleBean(DaeguChainStorageClient.class);
                assertThat(result.getBean(RegistryTarget.class)).isEqualTo(RegistryTarget.DAEGUCHAIN);
                // 쓰기 승인 전에는 워커·스케줄러를 만들지 않는다.
                assertThat(result).doesNotHaveBean(CredentialFabricWorker.class);
            });
    }

    @Test
    void fakeProviderDoesNotTouchTheDaeguchainBeansOrProperties() {
        context.withPropertyValues("dabaeum.blockchain.provider=fake")
            .run(result -> {
                assertThat(result).doesNotHaveBean(DaeguChainStorageClient.class);
                assertThat(result.getBean(RegistryTarget.class)).isEqualTo(RegistryTarget.FABRIC_POC);
            });
    }

    /**
     * 대구체인 안내서(DGBE_01)는 운영계 {@code https://daegu.go.kr}, 테스트베드
     * {@code http://192.168.50.107} 로 도메인만 바꿔 쓰도록 요구한다. 두 형태 모두 설정으로 받아
     * 경로 접두가 그대로 붙는지 본다.
     */
    @Test
    void bindsBothTestbedHttpAndProductionHttpsOrigins() {
        context.withPropertyValues(
                "dabaeum.blockchain.provider=daeguchain",
                "dabaeum.daeguchain.base-url=http://192.168.50.107",
                "dabaeum.daeguchain.token=field-token",
                "dabaeum.daeguchain.project-id=P")
            .run(result -> {
                DaeguChainProperties properties = result.getBean(DaeguChainProperties.class);
                assertThat(properties.baseUrl() + DaeguChainStorageClient.STORAGE_PREFIX)
                    .isEqualTo("http://192.168.50.107/daeguchain/v2/mitum/storage");
                assertThat(properties.token()).isEqualTo("field-token");
            });

        // 운영계는 https 이고, 끝의 슬래시는 붙여도 경로가 겹치지 않게 정리된다.
        context.withPropertyValues(
                "dabaeum.blockchain.provider=daeguchain",
                "dabaeum.daeguchain.base-url=https://daegu.go.kr/",
                "dabaeum.daeguchain.project-id=P")
            .run(result -> assertThat(
                result.getBean(DaeguChainProperties.class).baseUrl()
                    + DaeguChainStorageClient.STORAGE_PREFIX)
                .isEqualTo("https://daegu.go.kr/daeguchain/v2/mitum/storage"));
    }

    @Test
    void daeguchainProviderRequiresBaseUrlAndProjectId() {
        context.withPropertyValues("dabaeum.blockchain.provider=daeguchain")
            .run(result -> assertThat(result).hasFailed());
        context.withPropertyValues(
                "dabaeum.blockchain.provider=daeguchain",
                "dabaeum.daeguchain.base-url=ftp://bad",
                "dabaeum.daeguchain.project-id=P")
            .run(result -> assertThat(result).hasFailed());
    }
}
