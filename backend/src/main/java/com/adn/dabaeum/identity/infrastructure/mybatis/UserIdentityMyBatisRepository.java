package com.adn.dabaeum.identity.infrastructure.mybatis;

import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class UserIdentityMyBatisRepository
    implements UserIdentityRepository {

    private final UserIdentityMapper mapper;

    public UserIdentityMyBatisRepository(UserIdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(UserIdentity identity) {
        mapper.insert(toRow(identity));
    }

    @Override
    public Optional<UserIdentity> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<UserIdentity> findByProviderSubject(
        IdentityProvider provider,
        String providerSubject
    ) {
        return Optional.ofNullable(mapper.selectByProviderSubject(
            provider.name(),
            providerSubject
        )).map(this::toDomain);
    }

    @Override
    public List<UserIdentity> findByUserId(UUID userId) {
        return mapper.selectByUserId(userId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public boolean deleteByIdAndUserId(UUID identityId, UUID userId) {
        return mapper.deleteByIdAndUserId(identityId, userId) == 1;
    }

    private UserIdentityRow toRow(UserIdentity identity) {
        return new UserIdentityRow(
            identity.id(),
            identity.userId(),
            identity.provider().name(),
            identity.providerSubject(),
            identity.externalDid(),
            identity.verifiedAt(),
            identity.metadata(),
            identity.createdAt(),
            identity.updatedAt()
        );
    }

    private UserIdentity toDomain(UserIdentityRow row) {
        return new UserIdentity(
            row.id(),
            row.userId(),
            IdentityProvider.valueOf(row.provider()),
            row.providerSubject(),
            row.externalDid(),
            row.verifiedAt(),
            row.metadata(),
            row.createdAt(),
            row.updatedAt()
        );
    }
}
