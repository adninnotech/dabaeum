package com.adn.dabaeum.support.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository {

    void save(Notice notice);

    Optional<Notice> findById(UUID id);

    /**
     * {@code now} 는 예약 게시 규칙을 위한 기준 시각이다. {@code published_at} 이 아직 미래인 행은
     * status 가 PUBLISHED 여도 빠진다.
     */
    List<Notice> findPublishedPage(NoticeAudience audience, int limit, int offset, Instant now);

    long countPublished(NoticeAudience audience, Instant now);

    /** q(제목) 검색·audience·status 필터가 있는 관리 목록용. findAdminPage 와 조건이 반드시 같아야 한다. */
    List<Notice> findAdminPage(
        int limit, int offset, String keyword, NoticeAudience audience, NoticeStatus status);

    long countAll(String keyword, NoticeAudience audience, NoticeStatus status);

    boolean update(Notice notice);

    boolean delete(UUID id);
}
