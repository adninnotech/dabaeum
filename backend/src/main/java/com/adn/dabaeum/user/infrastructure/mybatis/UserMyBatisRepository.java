package com.adn.dabaeum.user.infrastructure.mybatis;

import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class UserMyBatisRepository implements UserRepository {

    private final UserMapper mapper;

    public UserMyBatisRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(User user) {
        mapper.insert(toRow(user));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(this::toDomain);
    }

    @Override
    public List<User> findPage(UserPageCriteria criteria) {
        return mapper.selectPage(criteria)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long count(UserStatus status) {
        return mapper.count(status);
    }

    @Override
    public boolean updateProfile(User user) {
        return mapper.updateProfile(toRow(user)) == 1;
    }

    @Override
    public boolean updateStatus(
        UUID id,
        UserStatus status,
        Instant withdrawnAt,
        Instant updatedAt
    ) {
        return mapper.updateStatus(id, status, withdrawnAt, updatedAt) == 1;
    }

    @Override
    public Optional<Instant> findAuthInvalidatedAt(UUID id) {
        return Optional.ofNullable(mapper.selectAuthInvalidatedAt(id));
    }

    @Override
    public boolean invalidateTokens(UUID id, Instant at) {
        return mapper.updateAuthInvalidatedAt(id, at) == 1;
    }

    private UserRow toRow(User user) {
        return new UserRow(
            user.id(),
            user.name(),
            user.email(),
            user.phone(),
            user.birthDate(),
            user.status().name(),
            user.withdrawnAt(),
            user.createdAt(),
            user.updatedAt(),
            user.career(),
            user.introduction(),
            user.profileImageId()
        );
    }

    private User toDomain(UserRow row) {
        return new User(
            row.id(),
            row.name(),
            row.email(),
            row.phone(),
            row.birthDate(),
            UserStatus.valueOf(row.status()),
            row.withdrawnAt(),
            row.createdAt(),
            row.updatedAt(),
            row.career(),
            row.introduction(),
            row.profileImageId()
        );
    }
}
