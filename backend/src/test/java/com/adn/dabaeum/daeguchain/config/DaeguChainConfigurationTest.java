package com.adn.dabaeum.daeguchain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.blockchain.config.BlockchainProviderConfiguration;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialStatusListAllocator;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.fabric.application.CredentialFabricIssuanceContextProvider;
import com.adn.dabaeum.fabric.application.CredentialFabricReconciler;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import java.time.Clock;
import org.springframework.transaction.PlatformTransactionManager;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.provider.DaeguChainStorageProvider;
import com.adn.dabaeum.fabric.application.CredentialFabricScheduler;
import com.adn.dabaeum.fabric.application.CredentialFabricWorker;
import java.time.Duration;
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

    @Test
    void storageClientCarriesTheConfiguredConnectTimeout() {
        // JdkClientHttpRequestFactory 에는 setConnectTimeout 이 없다. HttpClient 로 넘기지 않으면
        // connect-timeout 설정이 아무 데도 닿지 않고 읽기 제한시간이 연결까지 통째로 덮는다.
        DaeguChainProperties properties = new DaeguChainProperties(
            "http://127.0.0.1:8090", null, "dchain", "EVDCFTOIGQNUVJZDSYAP", null,
            Duration.ofSeconds(3), Duration.ofSeconds(30));

        assertThat(DaeguChainConfiguration.httpClient(properties).connectTimeout())
            .contains(Duration.ofSeconds(3));
    }

    @Test
    void writeApprovalOutsideLocalAndDevLeavesTheReadPathIntactInsteadOfFailingStartup() {
        // 워커·컨텍스트 제공자·리컨실러의 협력자가 모두 local·dev 전용이라, 다른 프로필에서
        // 쓰기 경로를 조립하려 들면 빈을 찾지 못해 기동이 죽는다. 읽기 경로는 남아야 한다.
        context.withPropertyValues(
                "dabaeum.blockchain.provider=daeguchain",
                "dabaeum.blockchain.write-enabled=true",
                "dabaeum.fabric.worker.enabled=true",
                "dabaeum.daeguchain.base-url=http://127.0.0.1:8090",
                "dabaeum.daeguchain.project-id=EVDCFTOIGQNUVJZDSYAP")
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).hasSingleBean(BlockchainRegistryPort.class);
                assertThat(result).doesNotHaveBean(CredentialFabricWorker.class);
                assertThat(result).doesNotHaveBean(CredentialFabricScheduler.class);
            });
    }

    /**
     * dev 프로필에서는 쓰기 경로가 전과 똑같이 조립돼야 한다. 서버가 이 프로필로 돌기 때문에,
     * 프로필 게이트를 넣으면서 워커·스케줄러를 조용히 꺼뜨리지 않았는지 여기서 못 박는다.
     */
    @Test
    void devProfileWithWriteApprovalStillAssemblesTheWorkerAndScheduler() {
        context.withPropertyValues(
                "spring.profiles.active=dev",
                "dabaeum.blockchain.provider=daeguchain",
                "dabaeum.blockchain.write-enabled=true",
                "dabaeum.fabric.worker.enabled=true",
                "dabaeum.fabric.worker.fixed-delay=1h",
                "dabaeum.fabric.worker.batch-size=10",
                "dabaeum.daeguchain.base-url=http://127.0.0.1:8090",
                "dabaeum.daeguchain.project-id=EVDCFTOIGQNUVJZDSYAP")
            .withBean(CredentialRepository.class, () -> mock(CredentialRepository.class))
            .withBean(CredentialGroupRepository.class, () -> mock(CredentialGroupRepository.class))
            .withBean(CompletionRepository.class, () -> mock(CompletionRepository.class))
            .withBean(EnrollmentRepository.class, () -> mock(EnrollmentRepository.class))
            .withBean(CourseRepository.class, () -> mock(CourseRepository.class))
            .withBean(CredentialDocumentFactory.class, () -> mock(CredentialDocumentFactory.class))
            .withBean(CredentialIdentifierProvider.class, () -> mock(CredentialIdentifierProvider.class))
            .withBean(CredentialStatusListAllocator.class, () -> mock(CredentialStatusListAllocator.class))
            .withBean(BlockchainTransactionRepository.class, () -> mock(BlockchainTransactionRepository.class))
            .withBean(CredentialProofService.class, () -> mock(CredentialProofService.class))
            .withBean(CredentialHashService.class, () -> mock(CredentialHashService.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(CredentialFabricReconciler.class, () -> mock(CredentialFabricReconciler.class))
            .withBean(Clock.class, () -> Clock.systemUTC())
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).hasSingleBean(CredentialFabricWorker.class);
                assertThat(result).hasSingleBean(CredentialFabricIssuanceContextProvider.class);
                assertThat(result).hasSingleBean(CredentialFabricScheduler.class);
            });
    }
}
