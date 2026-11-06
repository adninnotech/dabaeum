package com.adn.dabaeum.daeguchain.config;

import com.adn.dabaeum.blockchain.config.BlockchainProperties;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialSigningKeyGate;
import com.adn.dabaeum.credential.application.CredentialStatusListAllocator;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningInputFactory;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.provider.DaeguChainStorageProvider;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.fabric.application.CredentialFabricIssuanceContextProvider;
import com.adn.dabaeum.fabric.application.CredentialFabricReconciler;
import com.adn.dabaeum.fabric.application.CredentialFabricScheduler;
import com.adn.dabaeum.fabric.application.CredentialFabricWorker;
import com.adn.dabaeum.fabric.application.CredentialIssuanceSigner;
import com.adn.dabaeum.fabric.application.DefaultCredentialFabricIssuanceContextProvider;
import com.adn.dabaeum.fabric.application.DefaultCredentialFabricWorker;
import com.adn.dabaeum.fabric.application.SigningKeyFinalizeCheck;
import com.adn.dabaeum.fabric.application.TransitCredentialIssuanceSigner;
import com.adn.dabaeum.fabric.config.FabricWorkerProperties;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code dabaeum.blockchain.provider=daeguchain} 일 때의 원장 조립.
 *
 * <p>Fabric SDK 없이 REST 로 대구체인(또는 에뮬레이터)에 붙는다. 워커·컨텍스트 제공자·스케줄러는
 * Provider 중립이지만 지금까지 {@code fabric} 프로필 안에서만 조립됐으므로 여기서 프로필 없이
 * 다시 조립한다. Fabric 코드를 걷어내면 이 설정이 유일한 조립 지점이 된다.
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
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new DaeguChainStorageClient(restClient, objectMapper, properties);
    }

    @Bean
    BlockchainRegistryPort daeguChainRegistryPort(
        DaeguChainStorageClient client,
        BlockchainProperties blockchainProperties
    ) {
        return new DaeguChainStorageProvider(client, blockchainProperties.writeEnabled());
    }

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "write-enabled", havingValue = "true")
    CredentialFabricIssuanceContextProvider credentialFabricIssuanceContextProvider(
        CredentialRepository credentials, CredentialGroupRepository groups,
        CompletionRepository completions, EnrollmentRepository enrollments, CourseRepository courses,
        CredentialDocumentFactory documents, CredentialIdentifierProvider identifiers,
        CredentialStatusListAllocator statusLists
    ) {
        return new DefaultCredentialFabricIssuanceContextProvider(credentials, groups, completions,
            enrollments, courses, documents, identifiers, statusLists);
    }

    /**
     * provider=openbao 면 서명기가 파일 키 서명을 대신하므로 파일 키 서명 빈이 없어도 뜬다. 확정 검사는
     * local·dev 프로필에서 항상 주입된다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "write-enabled", havingValue = "true")
    CredentialFabricWorker credentialFabricWorker(
        BlockchainTransactionRepository transactions, CredentialRepository credentials,
        CredentialFabricIssuanceContextProvider contexts, ObjectProvider<CredentialProofService> proof,
        CredentialHashService hashes, BlockchainRegistryPort registry,
        PlatformTransactionManager transactionManager, BlockchainProperties blockchainProperties,
        ObjectProvider<CredentialIssuanceSigner> issuanceSigner,
        ObjectProvider<SigningKeyFinalizeCheck> finalizeCheck
    ) {
        return new DefaultCredentialFabricWorker(transactions, credentials, contexts, proof.getIfAvailable(),
            hashes, registry, transactionManager, blockchainProperties.target(),
            issuanceSigner.getIfAvailable(), finalizeCheck.getIfAvailable());
    }

    /** provider=openbao 면 수료증을 OpenBao Transit 으로 서명한다 (ADR-0006, 설계서 §8.1). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "write-enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "dabaeum.credential-proof", name = "provider", havingValue = "openbao")
    static class TransitSigningConfiguration {

        @Bean
        CredentialIssuanceSigner credentialIssuanceSigner(
            CredentialSigningRepository signing, CredentialRepository credentials,
            InstitutionSignKeyRepository keys, InstitutionSignKeyVersionRepository versions,
            CredentialSigningKeyGate gate, CredentialSigningInputFactory inputs,
            TransitSigningPort transit, CredentialHashService hashes
        ) {
            return new TransitCredentialIssuanceSigner(
                signing, credentials, keys, versions, gate, inputs, transit, hashes);
        }
    }

    /** 스케줄러는 백엔드 쓰기 승인과 워커 활성화가 모두 켜졌을 때만 돈다. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "write-enabled", havingValue = "true")
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
