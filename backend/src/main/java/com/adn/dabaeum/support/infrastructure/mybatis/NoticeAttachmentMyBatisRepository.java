package com.adn.dabaeum.support.infrastructure.mybatis;

import com.adn.dabaeum.support.domain.NoticeAttachment;
import com.adn.dabaeum.support.domain.NoticeAttachmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class NoticeAttachmentMyBatisRepository implements NoticeAttachmentRepository {

    private final NoticeAttachmentMapper mapper;

    public NoticeAttachmentMyBatisRepository(NoticeAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(NoticeAttachment attachment) {
        mapper.insert(toRow(attachment));
    }

    @Override
    public List<NoticeAttachment> findActiveByNoticeId(UUID noticeId) {
        return mapper.selectActiveByNoticeId(noticeId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<NoticeAttachment> findActiveById(UUID id) {
        return Optional.ofNullable(mapper.selectActiveById(id)).map(this::toDomain);
    }

    @Override
    public boolean softDelete(UUID id, Instant deletedAt) {
        return mapper.softDelete(id, deletedAt) == 1;
    }

    private NoticeAttachmentRow toRow(NoticeAttachment attachment) {
        return new NoticeAttachmentRow(
            attachment.id(),
            attachment.noticeId(),
            attachment.fileId(),
            attachment.fileName(),
            attachment.byteSize(),
            attachment.sortOrder(),
            attachment.createdAt()
        );
    }

    private NoticeAttachment toDomain(NoticeAttachmentRow row) {
        return new NoticeAttachment(
            row.id(),
            row.noticeId(),
            row.fileId(),
            row.fileName(),
            row.byteSize(),
            row.sortOrder(),
            row.createdAt()
        );
    }
}
