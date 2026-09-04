package com.adn.dabaeum.instructor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructorApplicationRepository {

    void save(InstructorApplication application);

    Optional<InstructorApplication> findById(UUID id);

    Optional<InstructorApplication> findByIdForUpdate(UUID id);

    Optional<InstructorApplication> findPending(UUID userId, UUID institutionId);

    List<InstructorApplication> findPage(InstructorApplicationPageCriteria criteria);

    long count(InstructorApplicationPageCriteria criteria);

    boolean updateReview(
        InstructorApplication application,
        InstructorApplicationStatus expectedStatus
    );
}
