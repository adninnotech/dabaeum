package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CredentialGroupMyBatisRepository implements CredentialGroupRepository {

    private final CredentialGroupMapper mapper;

    public CredentialGroupMyBatisRepository(CredentialGroupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CredentialGroup> findById(UUID groupId) {
        return Optional.ofNullable(mapper.selectById(groupId)).map(this::toDomain);
    }

    @Override
    public Optional<CredentialGroup> findByCompletionId(UUID completionId) {
        return Optional.ofNullable(mapper.selectByCompletionId(completionId)).map(this::toDomain);
    }

    @Override
    public Optional<CredentialGroup> findByCompletionIdForUpdate(UUID completionId) {
        return Optional.ofNullable(mapper.selectByCompletionIdForUpdate(completionId))
            .map(this::toDomain);
    }

    @Override
    public void insert(CredentialGroup group) {
        mapper.insert(new CredentialGroupRow(group.id(), group.completionId(), group.createdAt()));
    }

    private CredentialGroup toDomain(CredentialGroupRow row) {
        return new CredentialGroup(row.id(), row.completionId(), row.createdAt());
    }
}
