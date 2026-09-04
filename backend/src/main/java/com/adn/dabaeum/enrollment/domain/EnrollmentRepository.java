package com.adn.dabaeum.enrollment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    void save(Enrollment enrollment);

    Optional<Enrollment> findById(UUID enrollmentId);

    Optional<Enrollment> findByIdForUpdate(UUID enrollmentId);

    List<UUID> findInstitutionIdsByUserId(UUID userId);

    Optional<Enrollment> findActiveByCourseIdAndUserId(UUID courseId, UUID userId);

    List<Enrollment> findPageByCourseId(EnrollmentPageCriteria criteria);

    long countByCourseId(UUID courseId);

    long countApprovedByCourseId(UUID courseId);

    boolean updateState(Enrollment updated, EnrollmentStatus expectedStatus);
}
