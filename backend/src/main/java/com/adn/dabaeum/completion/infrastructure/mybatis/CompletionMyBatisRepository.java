package com.adn.dabaeum.completion.infrastructure.mybatis;

import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CompletionMyBatisRepository implements CompletionRepository {

    private final CompletionMapper mapper;

    public CompletionMyBatisRepository(CompletionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Completion completion) {
        mapper.insert(toRow(completion));
    }

    @Override
    public Optional<Completion> findById(UUID completionId) {
        return Optional.ofNullable(mapper.selectById(completionId)).map(this::toDomain);
    }

    @Override
    public Optional<Completion> findByIdForUpdate(UUID completionId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(completionId)).map(this::toDomain);
    }

    @Override
    public Optional<Completion> findByEnrollmentId(UUID enrollmentId) {
        return Optional.ofNullable(mapper.selectByEnrollmentId(enrollmentId)).map(this::toDomain);
    }

    @Override
    public Optional<Completion> findByEnrollmentIdForUpdate(UUID enrollmentId) {
        return Optional.ofNullable(mapper.selectByEnrollmentIdForUpdate(enrollmentId))
            .map(this::toDomain);
    }

    @Override
    public boolean updateEvaluation(Completion completion, CompletionStatus expectedStatus) {
        return mapper.updateEvaluation(toRow(completion), expectedStatus.name()) == 1;
    }

    @Override
    public boolean confirm(Completion completion, CompletionStatus expectedStatus) {
        return mapper.confirm(toRow(completion), expectedStatus.name()) == 1;
    }

    @Override
    public boolean revertConfirmation(Completion completion, CompletionStatus expectedStatus) {
        return mapper.revertConfirmation(toRow(completion), expectedStatus.name()) == 1;
    }

    private CompletionRow toRow(Completion completion) {
        return new CompletionRow(
            completion.id(), completion.enrollmentId(), completion.status().name(),
            completion.attendanceRate(), completion.completedMinutes(), completion.creditValue(),
            completion.evaluatedAt(), completion.completedAt(), completion.confirmedBy(),
            completion.confirmedAt(), completion.failureReason(), completion.createdAt(),
            completion.updatedAt());
    }

    private Completion toDomain(CompletionRow row) {
        return new Completion(
            row.id(), row.enrollmentId(), CompletionStatus.valueOf(row.status()),
            row.attendanceRate(), row.completedMinutes(), row.creditValue(), row.evaluatedAt(),
            row.completedAt(), row.confirmedBy(), row.confirmedAt(), row.failureReason(),
            row.createdAt(), row.updatedAt());
    }
}
