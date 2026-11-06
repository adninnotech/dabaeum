package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsApprovalConsumption;
import com.adn.dabaeum.credential.domain.KmsApprovalRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class KmsApprovalMyBatisRepository implements KmsApprovalRepository {

    private final KmsApprovalMapper mapper;

    public KmsApprovalMyBatisRepository(KmsApprovalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(KmsApproval approval) {
        mapper.insert(toRow(approval));
    }

    @Override
    public Optional<KmsApproval> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean consume(KmsApprovalConsumption consumption) {
        return mapper.consume(consumption.approvalId(), consumption.institutionId(),
            consumption.purpose().name(), consumption.action().name(), consumption.requester(),
            consumption.signKeyId(), consumption.currentVersion(), consumption.targetKeyVersion(),
            consumption.targetCompromisedAt(), consumption.paramsHash(), consumption.operationId(),
            consumption.now()) == 1;
    }

    @Override
    public List<KmsApproval> findOpenByInstitutionId(UUID institutionId, Instant now) {
        return mapper.selectOpenByInstitutionId(institutionId, now).stream().map(this::toDomain).toList();
    }

    static KmsApprovalRow toRow(KmsApproval approval) {
        return new KmsApprovalRow(approval.id(), approval.institutionId(), approval.purpose().name(),
            approval.action().name(), approval.signKeyId(), approval.expectedVersion(),
            approval.targetKeyVersion(), approval.targetCompromisedAt(), approval.paramsHash(),
            approval.approvedBy(), approval.expiresAt(), approval.consumedAt(), approval.consumedByOperationId(),
            approval.createdAt());
    }

    private KmsApproval toDomain(KmsApprovalRow row) {
        return new KmsApproval(row.id(), row.institutionId(), SignKeyPurpose.valueOf(row.purpose()),
            KmsAction.valueOf(row.action()), row.signKeyId(), row.expectedVersion(), row.targetKeyVersion(),
            row.targetCompromisedAt(), row.paramsHash(), row.approvedBy(), row.expiresAt(), row.consumedAt(),
            row.consumedByOperationId(), row.createdAt());
    }
}
