package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class EnrollmentMyBatisRepository implements EnrollmentRepository {

    private final EnrollmentMapper mapper;

    public EnrollmentMyBatisRepository(EnrollmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Enrollment enrollment) {
        mapper.insert(toRow(enrollment));
    }

    @Override
    public Optional<Enrollment> findById(UUID enrollmentId) {
        return Optional.ofNullable(mapper.selectById(enrollmentId)).map(this::toDomain);
    }

    @Override
    public Optional<Enrollment> findByIdForUpdate(UUID enrollmentId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(enrollmentId)).map(this::toDomain);
    }

    @Override
    public List<UUID> findInstitutionIdsByUserId(UUID userId) {
        return mapper.selectInstitutionIdsByUserId(userId).stream()
            .map(UUID::fromString)
            .toList();
    }

    @Override
    public Optional<Enrollment> findActiveByCourseIdAndUserId(UUID courseId, UUID userId) {
        return Optional.ofNullable(mapper.selectActiveByCourseIdAndUserId(courseId, userId))
            .map(this::toDomain);
    }

    @Override
    public List<Enrollment> findPageByCourseId(EnrollmentPageCriteria criteria) {
        return mapper.selectPage(criteria).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByCourseId(UUID courseId) {
        return mapper.countByCourseId(courseId);
    }

    @Override
    public long countApprovedByCourseId(UUID courseId) {
        return mapper.countApprovedByCourseId(courseId);
    }

    @Override
    public boolean updateState(Enrollment updated, EnrollmentStatus expectedStatus) {
        return mapper.updateState(toRow(updated), expectedStatus.name()) == 1;
    }

    private EnrollmentRow toRow(Enrollment enrollment) {
        return new EnrollmentRow(
            enrollment.id(), enrollment.courseId(), enrollment.userId(), enrollment.appliedBy(),
            enrollment.applicationType().name(), enrollment.status().name(), enrollment.appliedAt(),
            enrollment.approvedAt(), enrollment.rejectedAt(), enrollment.cancelledAt(),
            enrollment.withdrawnAt(), enrollment.rejectionReason(), enrollment.cancellationReason(),
            enrollment.createdAt(), enrollment.updatedAt());
    }

    private Enrollment toDomain(EnrollmentRow row) {
        return new Enrollment(
            row.id(), row.courseId(), row.userId(), row.appliedBy(),
            EnrollmentApplicationType.valueOf(row.applicationType()),
            EnrollmentStatus.valueOf(row.status()), row.appliedAt(), row.approvedAt(),
            row.rejectedAt(), row.cancelledAt(), row.withdrawnAt(), row.rejectionReason(),
            row.cancellationReason(), row.createdAt(), row.updatedAt());
    }
}
