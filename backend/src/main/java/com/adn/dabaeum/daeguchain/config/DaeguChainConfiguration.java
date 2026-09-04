package com.adn.dabaeum.daeguchain.config;

import com.adn.dabaeum.blockchain.config.BlockchainProperties;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialStatusListAllocator;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.provider.DaeguChainStorageProvider;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.fabric.application.CredentialFabricIssuanceContextProvider;
import com.adn.dabaeum.fabric.application.CredentialFabricReconciler;
import com.adn.dabaeum.fabric.application.CredentialFabricScheduler;
import com.adn.dabaeum.fabric.application.CredentialFabricWorker;
import com.adn.dabaeum.fabric.application.DefaultCredentialFabricIssuanceContextProvider;
import com.adn.dabaeum.fabric.application.DefaultCredentialFabricWorker;
import com.adn.dabaeum.fabric.config.FabricWorkerProperties;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code dabaeum.blockchain.provider=daeguchain} 일 때의 원장 조립.
 *
 * <p>Fabric SDK 없이 REST 로 대구체인(또는 에뮬레이터)에 붙는다. 읽기 경로(클라이언트·Provider)는
 * 프로필과 무관하게 조립하고, 원장에 쓰는 경로는 {@link WriteConfiguration} 안에 모은다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "provider", havingValue = "daeguchain")
@EnableConfigurationProperties(DaeguChainProperties.class)
public class DaeguChainConfiguration {

    @Bean
    DaeguChainStorageClient daeguChainStorageClient(
        DaeguChainProperties properties,
        ObjectMapper objectMapper
    ) {
        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(httpClient(properties));
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new DaeguChainStorageClient(restClient, objectMapper, properties);
    }

    /**
     * 연결 예산은 {@link HttpClient} 에만 줄 수 있다. {@link JdkClientHttpRequestFactory} 에는
     * {@code setConnectTimeout} 이 없어서, 여기서 만들어 넣지 않으면 {@code connect-timeout}
     * 설정이 아무 데도 닿지 않고 읽기 제한시간이 연결까지 통째로 덮는다.
     */
    static HttpClient httpClient(DaeguChainProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Bean
    BlockchainRegistryPort daeguChainRegistryPort(
        DaeguChainStorageClient client,
        BlockchainProperties blockchainProperties
    ) {
        return new DaeguChainStorageProvider(client, blockchainProperties.writeEnabled());
    }

    /**
     * 원장에 쓰는 경로. 워커·컨텍스트 제공자가 쓰는 저장소 구현과 리컨실러가 모두 {@code local}·
     * {@code dev} 프로필에만 있으므로, 같은 조건을 여기 한 곳에 적어 조립 조건과 맞춘다. 조건이
     * 어긋나면 다른 프로필에서 빈을 찾지 못해 기동이 죽는데, 그건 테스트로 드러나지 않는다.
     */
    @Configuration(proxyBeanMethods = false)
    @Profile({"local", "dev"})
    @ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "write-enabled", havingValue = "true")
    static class WriteConfiguration {

        @Bean
        CredentialFabricIssuanceContextProvider credentialFabricIssuanceContextProvider(
            CredentialRepository credentials, CredentialGroupRepository groups,
            CompletionRepository completions, EnrollmentRepository enrollments,
            CourseRepository courses, CredentialDocumentFactory documents,
            CredentialIdentifierProvider identifiers, CredentialStatusListAllocator statusLists
        ) {
            return new DefaultCredentialFabricIssuanceContextProvider(credentials, groups,
                completions, enrollments, courses, documents, identifiers, statusLists);
        }

        @Bean
        CredentialFabricWorker credentialFabricWorker(
            BlockchainTransactionRepository transactions, CredentialRepository credentials,
            CredentialFabricIssuanceContextProvider contexts, CredentialProofService proof,
            CredentialHashService hashes, BlockchainRegistryPort registry,
            PlatformTransactionManager transactionManager, BlockchainProperties blockchainProperties
        ) {
            return new DefaultCredentialFabricWorker(transactions, credentials, contexts, proof,
                hashes, registry, transactionManager, blockchainProperties.target());
        }

        /** 스케줄러는 워커 활성화까지 켜졌을 때만 돈다. */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(prefix = "dabaeum.fabric.worker", name = "enabled", havingValue = "true")
        @EnableConfigurationProperties(FabricWorkerProperties.class)
        @EnableScheduling
        static class SchedulerConfiguration {

            @Bean
            CredentialFabricScheduler credentialFabricScheduler(
                CredentialFabricWorker worker,
                CredentialFabricReconciler reconciler,
                FabricWorkerProperties properties,
                Clock clock
            ) {
                return new CredentialFabricScheduler(worker, reconciler, properties, clock);
            }
        }
    }
}
