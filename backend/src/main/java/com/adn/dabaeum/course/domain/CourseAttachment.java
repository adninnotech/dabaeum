package com.adn.dabaeum.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 과정에 붙은 첨부파일. 파일 자체는 {@code tb_files} 에 있고 여기는 연결만 한다.
 * {@code fileName}·{@code byteSize} 는 조회 시 {@code tb_files} 조인으로 채워질 뿐 저장되지 않는다.
 */
public record CourseAttachment(
    UUID id,
    UUID courseId,
    UUID fileId,
    String fileName,
    long byteSize,
    int sortOrder,
    Instant createdAt
) {

    public CourseAttachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
