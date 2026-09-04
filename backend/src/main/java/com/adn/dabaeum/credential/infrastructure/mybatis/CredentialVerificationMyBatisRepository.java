package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CredentialVerificationMyBatisRepository implements CredentialVerificationRepository {

    private final CredentialVerificationMapper mapper;

    public CredentialVerificationMyBatisRepository(CredentialVerificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(CredentialVerification verification) {
        mapper.insert(toRow(verification));
    }

    @Override
    public List<CredentialVerification> findByCredentialId(
        UUID credentialId, int limit, int offset, String sort
    ) {
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException("invalid verification page");
        }
        VerificationSort parsed = VerificationSort.from(sort);
        return mapper.selectByCredentialId(credentialId, limit, offset,
                parsed.field, parsed.direction).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByCredentialId(UUID credentialId) {
        return mapper.countByCredentialId(credentialId);
    }

    private CredentialVerificationRow toRow(CredentialVerification verification) {
        return new CredentialVerificationRow(
            verification.id(), verification.credentialId(), verification.presentedCredentialNo(),
            verification.presentedHash(), verification.verificationType(), verification.requesterType(),
            verification.requesterId(), verification.result().name(), verification.verifiedAt(),
            verification.requestIp(), verification.verificationHash(), verification.metadata(),
            verification.createdAt());
    }

    private CredentialVerification toDomain(CredentialVerificationRow row) {
        return new CredentialVerification(
            row.id(), row.credentialId(), row.presentedCredentialNo(), row.presentedHash(),
            row.verificationType(), row.requesterType(), row.requesterId(),
            CredentialVerificationResult.valueOf(row.result()), row.verifiedAt(), row.requestIp(),
            row.verificationHash(), row.metadata(), row.createdAt());
    }

    private enum VerificationSort {
        CREATED_AT_ASC("createdAt,asc", "created_at", "ASC"),
        CREATED_AT_DESC("createdAt,desc", "created_at", "DESC"),
        VERIFIED_AT_ASC("verifiedAt,asc", "verified_at", "ASC"),
        VERIFIED_AT_DESC("verifiedAt,desc", "verified_at", "DESC");

        private final String value;
        private final String field;
        private final String direction;

        VerificationSort(String value, String field, String direction) {
            this.value = value;
            this.field = field;
            this.direction = direction;
        }

        private static VerificationSort from(String value) {
            for (VerificationSort sort : values()) {
                if (sort.value.equals(value)) {
                    return sort;
                }
            }
            throw new IllegalArgumentException("Unsupported verification sort: " + value);
        }
    }
}
