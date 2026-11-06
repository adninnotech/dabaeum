package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseAttachment;
import com.adn.dabaeum.course.domain.CourseAttachmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseAttachmentMyBatisRepository implements CourseAttachmentRepository {

    private final CourseAttachmentMapper mapper;

    public CourseAttachmentMyBatisRepository(CourseAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CourseAttachment attachment) {
        mapper.insert(toRow(attachment));
    }

    @Override
    public List<CourseAttachment> findActiveByCourseId(UUID courseId) {
        return mapper.selectActiveByCourseId(courseId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CourseAttachment> findActiveById(UUID id) {
        return Optional.ofNullable(mapper.selectActiveById(id)).map(this::toDomain);
    }

    @Override
    public boolean softDelete(UUID id, Instant deletedAt) {
        return mapper.softDelete(id, deletedAt) == 1;
    }

    private CourseAttachmentRow toRow(CourseAttachment attachment) {
        return new CourseAttachmentRow(
            attachment.id(),
            attachment.courseId(),
            attachment.fileId(),
            attachment.fileName(),
            attachment.byteSize(),
            attachment.sortOrder(),
            attachment.createdAt()
        );
    }

    private CourseAttachment toDomain(CourseAttachmentRow row) {
        return new CourseAttachment(
            row.id(),
            row.courseId(),
            row.fileId(),
            row.fileName(),
            row.byteSize(),
            row.sortOrder(),
            row.createdAt()
        );
    }
}
