package com.adn.dabaeum.daeguchain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.daeguchain.client.DaeguChainStorageClient;
import com.adn.dabaeum.daeguchain.config.DaeguChainProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 에뮬레이터(→ Fabric 원장)를 읽는 확인. 원장에 쓰지 않는다.
 *
 * <pre>
 * DABAEUM_DAEGUCHAIN_BASE_URL=http://127.0.0.1:8090 \
 * DABAEUM_DAEGUCHAIN_PROJECT_ID=EVDCFTOIGQNUVJZDSYAP \
 * DABAEUM_DAEGUCHAIN_SAMPLE_KEY=LM8BYVM01BSQ60UA \
 * ./gradlew test --tests '*DaeguChainEmulatorReadTest*'
 * </pre>
 *
 * 개발 PC 에서는 {@code ssh -N -L 8090:127.0.0.1:8090 <서버>} 터널이 필요하다.
 */
@EnabledIfEnvironmentVariable(named = "DABAEUM_DAEGUCHAIN_BASE_URL", matches = ".+")
class DaeguChainEmulatorReadTest {

    private final DaeguChainStorageProvider provider = new DaeguChainStorageProvider(
        new DaeguChainStorageClient(RestClient.create(), new ObjectMapper(), new DaeguChainProperties(
            System.getenv("DABAEUM_DAEGUCHAIN_BASE_URL"),
            System.getenv("DABAEUM_DAEGUCHAIN_TOKEN"),
            "dchain",
            System.getenv("DABAEUM_DAEGUCHAIN_PROJECT_ID"),
            System.getenv("DABAEUM_DAEGUCHAIN_BASE_URL") + "/health",
            Duration.ofSeconds(5), Duration.ofSeconds(30))),
        false);

    @Test
    void readsAnExistingLedgerEntryThroughTheEmulator() {
        String key = System.getenv().getOrDefault("DABAEUM_DAEGUCHAIN_SAMPLE_KEY", "LM8BYVM01BSQ60UA");
        CredentialRegistryReference reference =
            new CredentialRegistryReference(BlockchainProvider.DAEGUCHAIN, key, null);

        CredentialRegistryState state = provider.getCredentialState(reference);
        assertThat(state.chainKey()).isEqualTo("DCSTORE:" + key);
        assertThat(state.vcHash()).matches("^[0-9a-f]{64}$");
        assertThat(state.provider()).isEqualTo(BlockchainProvider.DAEGUCHAIN);

        var history = provider.getCredentialHistory(reference, new BlockchainHistoryQuery(10, 0, false));
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).transactionId()).isNotBlank();
        assertThat(provider.ledgerHeight()).isPresent();
    }

    @Test
    void reportsAnUnknownKeyAsNotFound() {
        String random = UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 16);
        assertThatThrownBy(() -> provider.getCredentialState(
                new CredentialRegistryReference(BlockchainProvider.DAEGUCHAIN, random, null)))
            .isInstanceOfSatisfying(BlockchainRegistryException.class,
                e -> assertThat(e.code()).isEqualTo("BLOCKCHAIN_NOT_FOUND"));
    }
}
