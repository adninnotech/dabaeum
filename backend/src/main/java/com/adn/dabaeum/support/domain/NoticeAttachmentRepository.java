package com.adn.dabaeum.support.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeAttachmentRepository {

    void save(NoticeAttachment attachment);

    /** 파일명·크기가 tb_files 조인으로 채워진 목록. sort_order, created_at 순. */
    List<NoticeAttachment> findActiveByNoticeId(UUID noticeId);

    Optional<NoticeAttachment> findActiveById(UUID id);

    boolean softDelete(UUID id, Instant deletedAt);
}
