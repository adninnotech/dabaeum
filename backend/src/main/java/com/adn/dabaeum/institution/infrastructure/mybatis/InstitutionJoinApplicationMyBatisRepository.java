package com.adn.dabaeum.institution.infrastructure.mybatis;

import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationRepository;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InstitutionJoinApplicationMyBatisRepository
    implements InstitutionJoinApplicationRepository {

    private final InstitutionJoinApplicationMapper mapper;

    public InstitutionJoinApplicationMyBatisRepository(
        InstitutionJoinApplicationMapper mapper
    ) {
        this.mapper = mapper;
    }

    @Override
    public void save(InstitutionJoinApplication application) {
        mapper.insert(toRow(application));
    }

    @Override
    public Optional<InstitutionJoinApplication> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<InstitutionJoinApplication> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id)).map(this::toDomain);
    }

    @Override
    public List<InstitutionJoinApplication> findPage(
        InstitutionJoinApplicationStatus status, int limit, int offset
    ) {
        return mapper.selectPage(
                status == null ? null : status.name(), limit, offset)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(InstitutionJoinApplicationStatus status) {
        return mapper.count(status == null ? null : status.name());
    }

    @Override
    public boolean updateDecision(
        InstitutionJoinApplication application,
        InstitutionJoinApplicationStatus expectedStatus
    ) {
        return mapper.updateDecision(toRow(application), expectedStatus.name()) == 1;
    }

    private InstitutionJoinApplicationRow toRow(InstitutionJoinApplication application) {
        return new InstitutionJoinApplicationRow(
            application.id(), application.institutionName(),
            application.institutionCode(), application.representativeName(),
            application.contactEmail(), application.contactPhone(),
            application.address(), application.status().name(),
            application.rejectionReason(), application.applicantUserId(),
            application.decidedBy(), application.decidedAt(),
            application.createdInstitutionId(),
            application.createdAt(), application.updatedAt());
    }

    private InstitutionJoinApplication toDomain(InstitutionJoinApplicationRow row) {
        return new InstitutionJoinApplication(
            row.id(), row.institutionName(), row.institutionCode(),
            row.representativeName(), row.contactEmail(), row.contactPhone(),
            row.address(),
            InstitutionJoinApplicationStatus.valueOf(row.status()),
            row.rejectionReason(), row.applicantUserId(), row.decidedBy(),
            row.decidedAt(), row.createdInstitutionId(),
            row.createdAt(), row.updatedAt());
    }
}
