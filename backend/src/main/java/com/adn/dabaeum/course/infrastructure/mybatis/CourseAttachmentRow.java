package com.adn.dabaeum.course.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/**
 * insert 에는 {@code fileName}·{@code byteSize} 를 쓰지 않는다. 그 값은 {@code tb_files}
 * 조인으로만 채워지는 조회 전용 필드다.
 */
public record CourseAttachmentRow(
    UUID id,
    UUID courseId,
    UUID fileId,
    String fileName,
    Long byteSize,
    Integer sortOrder,
    Instant createdAt
) {
}
