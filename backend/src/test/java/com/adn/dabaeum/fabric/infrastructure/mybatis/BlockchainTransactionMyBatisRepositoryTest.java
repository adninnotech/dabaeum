package com.adn.dabaeum.fabric.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * network 목록은 IN 절에 그대로 들어가므로 허용 목록으로 지킨다. 새 Provider(DAEGUCHAIN)의
 * 워커가 집어가는 목록이 여기서 거부되면 앵커링이 조용히 멈추므로 대상별로 확인한다.
 */
class BlockchainTransactionMyBatisRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private final BlockchainTransactionMapper mapper = mock(BlockchainTransactionMapper.class);
    private final BlockchainTransactionMyBatisRepository repository =
        new BlockchainTransactionMyBatisRepository(mapper);

    @Test
    void acceptsTheNetworksEveryRegistryTargetClaims() {
        for (RegistryTarget target : List.of(RegistryTarget.DAEGUCHAIN, RegistryTarget.FABRIC_POC)) {
            when(mapper.claimDue(eq(target.supportedNetworks()), anyInt(), eq(NOW))).thenReturn(List.of());
            when(mapper.claimStale(eq(target.supportedNetworks()), anyInt(), eq(NOW), eq(NOW))).thenReturn(List.of());

            repository.claimDue(target.supportedNetworks(), 10, NOW);
            repository.claimStale(target.supportedNetworks(), 10, NOW, NOW);

            verify(mapper).claimDue(target.supportedNetworks(), 10, NOW);
            verify(mapper).claimStale(target.supportedNetworks(), 10, NOW, NOW);
        }
    }

    @Test
    void rejectsUnknownOrEmptyNetworkLists() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> repository.claimDue(List.of("DAEGUCHAIN", "MAINNET"), 10, NOW));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> repository.claimDue(List.of(), 10, NOW));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> repository.claimStale(List.of("'; DROP TABLE"), 10, NOW, NOW));
    }
}
