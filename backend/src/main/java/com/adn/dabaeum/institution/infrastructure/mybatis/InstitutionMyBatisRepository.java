package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
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
    public List<Institution> findActivePage(InstitutionPageCriteria criteria) {
        return mapper.selectActivePage(criteria)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countActive() {
        return mapper.countActive();
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
            institution.deletedAt()
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
            row.deletedAt()
        );
    }
}
