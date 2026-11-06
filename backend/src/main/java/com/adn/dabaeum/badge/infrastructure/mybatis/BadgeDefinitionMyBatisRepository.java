package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class BadgeDefinitionMyBatisRepository implements BadgeDefinitionRepository {

    private final BadgeDefinitionMapper mapper;

    public BadgeDefinitionMyBatisRepository(BadgeDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(BadgeDefinition definition) {
        mapper.insert(toRow(definition));
    }

    @Override
    public Optional<BadgeDefinition> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(BadgeDefinitionMyBatisRepository::toDomain);
    }

    @Override
    public List<BadgeDefinition> findAll(boolean activeOnly) {
        return mapper.selectAll(activeOnly).stream().map(BadgeDefinitionMyBatisRepository::toDomain).toList();
    }

    @Override
    public boolean update(BadgeDefinition definition) {
        return mapper.update(toRow(definition)) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return mapper.delete(id) == 1;
    }

    @Override
    public boolean hasIssuedBadges(UUID id) {
        return mapper.existsIssued(id);
    }

    private static BadgeDefinitionRow toRow(BadgeDefinition definition) {
        return new BadgeDefinitionRow(
            definition.id(), definition.code(), definition.name(), definition.description(),
            definition.imageFileId(), definition.rule().type().name(), BadgeRuleCodec.toJson(definition.rule()),
            definition.active(), definition.createdAt(), definition.updatedAt());
    }

    private static BadgeDefinition toDomain(BadgeDefinitionRow row) {
        return new BadgeDefinition(
            row.id(), row.code(), row.name(), row.description(), row.imageFileId(),
            BadgeRuleCodec.fromJson(row.ruleType(), row.ruleParams()), row.active(), row.createdAt(), row.updatedAt());
    }
}
