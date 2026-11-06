package com.adn.dabaeum.course.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomCourseAttachmentIdGenerator implements CourseAttachmentIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
