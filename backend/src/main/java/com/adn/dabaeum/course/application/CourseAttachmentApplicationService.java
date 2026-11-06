package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseAttachment;
import java.util.List;
import java.util.UUID;

public interface CourseAttachmentApplicationService {

    List<CourseAttachment> list(UUID courseId);

    CourseAttachment attach(
        AttachCourseAttachmentCommand command,
        AuthenticatedUserContext context
    );

    CourseAttachment remove(UUID courseId, UUID attachmentId, AuthenticatedUserContext context);
}
