package com.adn.dabaeum.support.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 공지에 붙은 첨부파일. course.domain.CourseAttachment 와 같은 모양이다. 파일 자체는
 * {@code tb_files} 에 있고 여기는 연결만 한다. {@code fileName}·{@code byteSize} 는 조회 시
 * {@code tb_files} 조인으로 채워질 뿐 저장되지 않는다.
 */
public record NoticeAttachment(
    UUID id,
    UUID noticeId,
    UUID fileId,
    String fileName,
    long byteSize,
    int sortOrder,
    Instant createdAt
) {

    public NoticeAttachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(noticeId, "noticeId");
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
