package com.adn.dabaeum.support.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notice(
    UUID id,
    String title,
    String body,
    NoticeAudience audience,
    NoticeStatus status,
    Instant publishedAt,
    UUID createdBy,
    /**
     * 작성자 표시 이름. tb_notices 에는 저장되지 않고 tb_users 조인으로만 채워지는 조회 전용
     * 필드다(CourseAttachment.fileName 과 같은 패턴). 작성자가 탈퇴해도 공지는 남아야 하므로
     * 조인이 안 맞으면 null 이 된다.
     */
    String authorName,
    Instant createdAt,
    Instant updatedAt
) {

    public Notice {
        Objects.requireNonNull(id, "id");
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title must be 1..200 characters");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (status == NoticeStatus.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("published notice requires publishedAt");
        }
        if (status == NoticeStatus.DRAFT && publishedAt != null) {
            throw new IllegalArgumentException("draft notice must not have publishedAt");
        }
    }
}
