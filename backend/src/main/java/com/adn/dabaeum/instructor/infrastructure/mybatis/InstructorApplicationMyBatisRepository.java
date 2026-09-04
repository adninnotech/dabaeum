package com.adn.dabaeum.instructor.infrastructure.mybatis;

import com.adn.dabaeum.instructor.domain.InstructorApplication;
import com.adn.dabaeum.instructor.domain.InstructorApplicationPageCriteria;
import com.adn.dabaeum.instructor.domain.InstructorApplicationRepository;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InstructorApplicationMyBatisRepository
    implements InstructorApplicationRepository {

    private final InstructorApplicationMapper mapper;

    public InstructorApplicationMyBatisRepository(InstructorApplicationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(InstructorApplication application) {
        mapper.insert(toRow(application));
    }

    @Override
    public Optional<InstructorApplication> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<InstructorApplication> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id)).map(this::toDomain);
    }

    @Override
    public Optional<InstructorApplication> findPending(UUID userId, UUID institutionId) {
        return Optional.ofNullable(mapper.selectPending(userId, institutionId))
            .map(this::toDomain);
    }

    @Override
    public List<InstructorApplication> findPage(
        InstructorApplicationPageCriteria criteria
    ) {
        return mapper.selectPage(criteria).stream().map(this::toDomain).toList();
    }

    @Override
    public long count(InstructorApplicationPageCriteria criteria) {
        return mapper.count(criteria);
    }

    @Override
    public boolean updateReview(
        InstructorApplication application,
        InstructorApplicationStatus expectedStatus
    ) {
        return mapper.updateReview(toRow(application), expectedStatus) == 1;
    }

    private InstructorApplicationRow toRow(InstructorApplication application) {
        return new InstructorApplicationRow(
            application.id(), application.userId(), application.institutionId(),
            application.status().name(), application.applicationMessage(),
            application.rejectionReason(), application.reviewedBy(),
            application.appliedAt(), application.reviewedAt(), application.createdAt(),
            application.updatedAt()
        );
    }

    private InstructorApplication toDomain(InstructorApplicationRow row) {
        return new InstructorApplication(
            row.id(), row.userId(), row.institutionId(),
            InstructorApplicationStatus.valueOf(row.status()),
            row.applicationMessage(), row.rejectionReason(), row.reviewedBy(),
            row.appliedAt(), row.reviewedAt(), row.createdAt(), row.updatedAt()
        );
    }
}
