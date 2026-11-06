package com.adn.dabaeum.administrator.infrastructure.mybatis;

import com.adn.dabaeum.administrator.domain.Administrator;
import com.adn.dabaeum.administrator.domain.AdministratorCriteria;
import com.adn.dabaeum.administrator.domain.AdministratorPage;
import com.adn.dabaeum.administrator.domain.AdministratorRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class AdministratorMyBatisRepository implements AdministratorRepository {

    private final AdministratorMapper mapper;

    public AdministratorMyBatisRepository(AdministratorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AdministratorPage search(AdministratorCriteria criteria) {
        return new AdministratorPage(
            mapper.search(criteria).stream().map(this::toDomain).toList(), mapper.count(criteria));
    }

    @Override
    public Optional<Administrator> findById(UUID userId, Instant now) {
        return Optional.ofNullable(mapper.selectById(userId, now)).map(this::toDomain);
    }

    private Administrator toDomain(AdministratorRow row) {
        return new Administrator(
            row.userId(), row.name(), row.email(), UserStatus.valueOf(row.status()),
            List.of(), row.lastLoginAt(), row.lockedUntil(), row.passwordChangedAt(), row.createdAt());
    }
}
