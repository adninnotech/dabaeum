package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationConflictException;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class KmsOperationMyBatisRepository implements KmsOperationRepository {

    private static final String IN_PROGRESS_INDEX = "uq_tb_kms_operations_in_progress";

    private final KmsOperationMapper mapper;

    public KmsOperationMyBatisRepository(KmsOperationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(KmsOperation operation) {
        try {
            mapper.insert(toRow(operation));
        } catch (DuplicateKeyException exception) {
            throw new KmsOperationConflictException(kind(exception));
        }
    }

    @Override
    public Optional<KmsOperation> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<KmsOperation> findInProgressBySignKeyId(UUID signKeyId) {
        return Optional.ofNullable(mapper.selectInProgressBySignKeyId(signKeyId)).map(this::toDomain);
    }

    @Override
    public List<KmsOperation> findByInstitutionId(UUID institutionId, int limit) {
        return mapper.selectByInstitutionId(institutionId, limit).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KmsOperation> findInProgress(int limit) {
        return mapper.selectInProgress(limit).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean acquireOwnership(UUID id, String owner, Instant now, Instant leaseUntil) {
        return mapper.acquireOwnership(id, owner, now, leaseUntil) == 1;
    }

    @Override
    public boolean reconfirmOwnership(UUID id, String owner, KmsOperationStatus expectedStatus, Instant now,
        Instant leaseUntil) {
        return mapper.reconfirmOwnership(id, owner, expectedStatus.name(), now, leaseUntil) == 1;
    }

    @Override
    public boolean update(KmsOperation operation) {
        return mapper.update(toRow(operation)) == 1;
    }

    @Override
    public boolean releaseOwnership(UUID id, String owner) {
        return mapper.releaseOwnership(id, owner) == 1;
    }

    /** PostgreSQL 은 위반한 제약·인덱스 이름을 메시지에 넣는다. 진행 중 인덱스가 아니면 멱등키 충돌이다. */
    private static KmsOperationConflictException.Kind kind(DuplicateKeyException exception) {
        String message = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        return message.contains(IN_PROGRESS_INDEX)
            ? KmsOperationConflictException.Kind.IN_PROGRESS
            : KmsOperationConflictException.Kind.IDEMPOTENCY;
    }

    static KmsOperationRow toRow(KmsOperation operation) {
        return new KmsOperationRow(operation.id(), operation.signKeyId(), operation.institutionId(),
            operation.purpose().name(), operation.action().name(), operation.status().name(),
            operation.targetKeyVersion(), operation.expectedVersionBefore(), operation.observedVersionAfter(),
            operation.preparedAt(), operation.lastCallAt(), operation.owner(), operation.leaseUntil(),
            operation.beforeValue(), operation.afterValue(), operation.reason(), operation.requestedBy(),
            operation.approvalId(), operation.idempotencyKey(), operation.createdAt(), operation.updatedAt());
    }

    private KmsOperation toDomain(KmsOperationRow row) {
        return new KmsOperation(row.id(), row.signKeyId(), row.institutionId(),
            SignKeyPurpose.valueOf(row.purpose()), KmsAction.valueOf(row.action()),
            KmsOperationStatus.valueOf(row.status()), row.targetKeyVersion(), row.expectedVersionBefore(),
            row.observedVersionAfter(), row.preparedAt(), row.lastCallAt(), row.owner(), row.leaseUntil(),
            row.beforeValue(), row.afterValue(), row.reason(), row.requestedBy(), row.approvalId(),
            row.idempotencyKey(), row.createdAt(), row.updatedAt());
    }
}
