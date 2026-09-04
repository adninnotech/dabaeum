package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class LearningBadgeMyBatisRepository implements LearningBadgeRepository {

    private final LearningBadgeMapper mapper;

    public LearningBadgeMyBatisRepository(LearningBadgeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(LearningBadge badge) {
        mapper.insert(toRow(badge));
    }

    @Override
    public Optional<LearningBadge> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<LearningBadge> findByCredentialIdAndTypeAndName(
        UUID credentialId, String badgeType, String badgeName
    ) {
        return Optional.ofNullable(mapper.selectByCredentialIdAndTypeAndName(
            credentialId, badgeType, badgeName)).map(this::toDomain);
    }

    @Override
    public List<LearningBadge> findByUserId(UUID userId, int limit, int offset, String sort) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        BadgeSort badgeSort = BadgeSort.from(sort);
        return mapper.selectByUserId(userId, limit, offset,
                badgeSort.field, badgeSort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countByUserId(UUID userId) {
        return mapper.countByUserId(userId);
    }

    private LearningBadgeRow toRow(LearningBadge badge) {
        return new LearningBadgeRow(
            badge.id(), badge.userId(), badge.courseId(), badge.credentialId(),
            badge.badgeType(), badge.badgeName(), badge.status().name(),
            badge.nftTokenId(), badge.issuedAt(), badge.revokedAt(),
            badge.createdAt(), badge.updatedAt());
    }

    private LearningBadge toDomain(LearningBadgeRow row) {
        return new LearningBadge(
            row.id(), row.userId(), row.courseId(), row.credentialId(),
            row.badgeType(), row.badgeName(), LearningBadgeStatus.valueOf(row.status()),
            row.nftTokenId(), row.issuedAt(), row.revokedAt(),
            row.createdAt(), row.updatedAt());
    }

    private enum BadgeSort {
        CREATED_AT_ASC("createdAt,asc", "created_at", "ASC"),
        CREATED_AT_DESC("createdAt,desc", "created_at", "DESC"),
        ISSUED_AT_ASC("issuedAt,asc", "issued_at", "ASC"),
        ISSUED_AT_DESC("issuedAt,desc", "issued_at", "DESC"),
        UPDATED_AT_ASC("updatedAt,asc", "updated_at", "ASC"),
        UPDATED_AT_DESC("updatedAt,desc", "updated_at", "DESC");

        private final String value;
        private final String field;
        private final String direction;

        BadgeSort(String value, String field, String direction) {
            this.value = value;
            this.field = field;
            this.direction = direction;
        }

        private static BadgeSort from(String value) {
            for (BadgeSort sort : values()) {
                if (sort.value.equals(value)) {
                    return sort;
                }
            }
            throw new IllegalArgumentException("Unsupported badge sort: " + value);
        }
    }
}
