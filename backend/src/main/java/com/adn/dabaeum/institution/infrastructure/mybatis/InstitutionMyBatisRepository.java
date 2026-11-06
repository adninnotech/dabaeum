package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InstitutionMyBatisRepository
    implements InstitutionRepository {

    private final InstitutionMapper mapper;

    public InstitutionMyBatisRepository(InstitutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Institution institution) {
        mapper.insert(toRow(institution));
    }

    @Override
    public Optional<Institution> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(this::toDomain);
    }

    @Override
    public List<Institution> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(List.copyOf(ids))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<Institution> findActiveById(UUID id) {
        return Optional.ofNullable(mapper.selectActiveById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Institution> findActiveByCode(
        String institutionCode
    ) {
        return Optional.ofNullable(
            mapper.selectActiveByCode(institutionCode)
        ).map(this::toDomain);
    }

    @Override
    public List<InstitutionListItem> findActivePage(InstitutionPageCriteria criteria) {
        return mapper.selectActivePage(criteria)
            .stream()
            .map(this::toListItem)
            .toList();
    }

    @Override
    public long countActive(String keyword, InstitutionStatus status, String region) {
        return mapper.countActiveFiltered(keyword, status, region);
    }

    @Override
    public boolean updateActive(Institution institution) {
        return mapper.updateActive(toRow(institution)) == 1;
    }

    private InstitutionRow toRow(Institution institution) {
        return new InstitutionRow(
            institution.id(),
            institution.institutionCode(),
            institution.name(),
            institution.businessNumber(),
            institution.representativeName(),
            institution.address(),
            institution.contactPhone(),
            institution.contactEmail(),
            institution.status().name(),
            institution.createdAt(),
            institution.updatedAt(),
            institution.deletedAt(),
            institution.homepageUrl(),
            institution.region(),
            institution.logoFileId(),
            institution.introduction()
        );
    }

    private Institution toDomain(InstitutionRow row) {
        return new Institution(
            row.id(),
            row.institutionCode(),
            row.name(),
            row.businessNumber(),
            row.representativeName(),
            row.address(),
            row.contactPhone(),
            row.contactEmail(),
            InstitutionStatus.valueOf(row.status()),
            row.createdAt(),
            row.updatedAt(),
            row.deletedAt(),
            row.homepageUrl(),
            row.region(),
            row.logoFileId(),
            row.introduction()
        );
    }

    private InstitutionListItem toListItem(InstitutionListRow row) {
        Institution institution = new Institution(
            row.id(),
            row.institutionCode(),
            row.name(),
            row.businessNumber(),
            row.representativeName(),
            row.address(),
            row.contactPhone(),
            row.contactEmail(),
            InstitutionStatus.valueOf(row.status()),
            row.createdAt(),
            row.updatedAt(),
            row.deletedAt(),
            row.homepageUrl(),
            row.region(),
            row.logoFileId(),
            row.introduction()
        );
        return new InstitutionListItem(
            institution,
            row.instructorCount() == null ? 0L : row.instructorCount(),
            row.courseCount() == null ? 0L : row.courseCount(),
            row.learnerCount() == null ? 0L : row.learnerCount(),
            row.adminCount() == null ? 0L : row.adminCount(),
            row.primaryAdminName()
        );
    }
}
