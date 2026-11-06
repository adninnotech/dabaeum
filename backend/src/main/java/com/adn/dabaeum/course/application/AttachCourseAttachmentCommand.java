package com.adn.dabaeum.course.application;

import java.util.UUID;

public record AttachCourseAttachmentCommand(
    UUID courseId,
    UUID fileId
) {
}
