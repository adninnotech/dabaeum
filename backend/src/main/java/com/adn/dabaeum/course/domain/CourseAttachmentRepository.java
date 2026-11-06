package com.adn.dabaeum.course.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseAttachmentRepository {

    void save(CourseAttachment attachment);

    /** 파일명·크기가 tb_files 조인으로 채워진 목록. sort_order, created_at 순. */
    List<CourseAttachment> findActiveByCourseId(UUID courseId);

    Optional<CourseAttachment> findActiveById(UUID id);

    boolean softDelete(UUID id, Instant deletedAt);
}
