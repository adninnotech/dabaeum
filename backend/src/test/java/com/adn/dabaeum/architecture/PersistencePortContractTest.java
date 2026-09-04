package com.adn.dabaeum.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.BlockchainRequestPort;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistencePortContractTest {

    @Test
    void requiredPersistenceOperationsHaveNoSilentDefaultFallbacks() throws Exception {
        Method[] requiredOperations = {
            CredentialRepository.class.getMethod("findByCredentialNo", String.class),
            CredentialRepository.class.getMethod("findByCredentialHash", String.class),
            BlockchainTransactionRepository.class.getMethod(
                "claimStale", List.class, int.class, Instant.class, Instant.class),
            BlockchainRequestPort.class.getMethod("hasPendingOperationForGroup", UUID.class)
        };

        assertThat(requiredOperations).allSatisfy(operation ->
            assertThat(operation.isDefault())
                .as("%s는 구현 누락을 기본 반환값으로 숨기면 안 된다", operation)
                .isFalse()
        );
    }
}
