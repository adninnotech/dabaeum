package com.adn.dabaeum.support.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository {

    void save(Notice notice);

    Optional<Notice> findById(UUID id);

    List<Notice> findPublishedPage(NoticeAudience audience, int limit, int offset);

    long countPublished(NoticeAudience audience);

    List<Notice> findAdminPage(int limit, int offset);

    long countAll();

    boolean update(Notice notice);

    boolean delete(UUID id);
}
