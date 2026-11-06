package com.adn.dabaeum.nft.config;

import com.adn.dabaeum.nft.application.NftIssuanceEnqueuer;
import com.adn.dabaeum.nft.application.NftIssuanceReconciler;
import com.adn.dabaeum.nft.application.NftIssuanceScheduler;
import com.adn.dabaeum.nft.application.NftIssuanceWorker;
import com.adn.dabaeum.nft.application.NftMetadataFactory;
import com.adn.dabaeum.nft.application.NftRetryPolicy;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftSourceRepository;
import com.adn.dabaeum.nft.infrastructure.daeguchain.DaeguChainNftLedger;
import com.adn.dabaeum.nft.infrastructure.daeguchain.DaeguChainNftProperties;
import com.adn.dabaeum.nft.infrastructure.fake.FakeNftLedger;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * NFT 발행 조립. {@code dabaeum.nft.enabled=true} 일 때만 빈을 만든다.
 * 원장은 {@code dabaeum.nft.ledger} 로 메모리(fake)와 실제 대구체인(daeguchain) 중 하나를 꽂는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dabaeum.nft", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NftProperties.class)
@EnableScheduling
public class NftConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.nft", name = "ledger", havingValue = NftProperties.FAKE,
        matchIfMissing = true)
    NftLedgerPort fakeNftLedger(Clock clock) {
        return new FakeNftLedger(clock.instant());
    }

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.nft", name = "ledger", havingValue = NftProperties.DAEGUCHAIN)
    NftLedgerPort daeguChainNftLedger(NftProperties properties, ObjectMapper objectMapper) {
        NftProperties.Daeguchain chain = properties.daeguchain();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(chain.readTimeout());
        return new DaeguChainNftLedger(RestClient.builder().requestFactory(requestFactory).build(), objectMapper,
            new DaeguChainNftProperties(chain.baseUrl(), chain.token(), chain.chain(), properties.walletAddress(),
                properties.walletPrivateKey(), chain.readTimeout()));
    }

    @Bean
    NftIssuanceScheduler nftIssuanceScheduler(NftProperties properties, NftIssuanceRepository issuances,
        NftSourceRepository sources, NftLedgerPort ledger, ObjectMapper objectMapper, Clock clock) {
        NftProperties.Worker worker = properties.worker();
        NftIssuanceEnqueuer enqueuer = new NftIssuanceEnqueuer(issuances, sources,
            new NftMetadataFactory(objectMapper, properties.mode()), properties.startAt(), properties.metadataBaseUrl(),
            UUID::randomUUID);
        NftIssuanceWorker sender = new NftIssuanceWorker(issuances, ledger, properties.walletAddress(),
            properties.badgeContractAddress(), properties.certificateContractAddress(),
            new NftRetryPolicy(worker.maxAttempts(), worker.retryBaseDelay()));
        NftIssuanceReconciler reconciler = new NftIssuanceReconciler(issuances, ledger,
            properties.walletAddress(), worker.submittingStaleAfter());
        return new NftIssuanceScheduler(enqueuer, sender, reconciler, worker.batchSize(), clock);
    }
}
