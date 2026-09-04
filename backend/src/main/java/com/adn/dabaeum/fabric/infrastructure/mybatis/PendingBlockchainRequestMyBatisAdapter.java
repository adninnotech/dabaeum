package com.adn.dabaeum.fabric.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.BlockchainRequestPort;
import com.adn.dabaeum.credential.domain.PendingBlockchainRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class PendingBlockchainRequestMyBatisAdapter implements BlockchainRequestPort {

    private final PendingBlockchainRequestMapper mapper;

    public PendingBlockchainRequestMyBatisAdapter(PendingBlockchainRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PendingBlockchainRequest> findByIdempotencyKey(
        String network,
        String idempotencyKey
    ) {
        return Optional.ofNullable(mapper.selectByNetworkAndIdempotencyKey(
            network, idempotencyKey))
            .map(this::toDomain);
    }

    @Override
    public void createAnchor(PendingBlockchainRequest request) {
        mapper.insert(new PendingBlockchainRequestRow(
            request.transactionId(), request.credentialId(), request.transactionType(),
            request.network(), request.idempotencyKey(), request.requestHash(), request.requestedAt(),
            request.operationReason()));
    }

    @Override
    public boolean hasPendingOperationForGroup(UUID credentialGroupId) {
        return mapper.existsPendingOperationForGroup(credentialGroupId);
    }

    private PendingBlockchainRequest toDomain(PendingBlockchainRequestRow row) {
        return new PendingBlockchainRequest(
            row.transactionId(), row.credentialId(), row.transactionType(), row.network(),
            row.idempotencyKey(), row.requestHash(), row.requestedAt(), row.operationReason());
    }
}
