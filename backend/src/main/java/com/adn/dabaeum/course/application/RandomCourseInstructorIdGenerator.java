package com.adn.dabaeum.course.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomCourseInstructorIdGenerator implements CourseInstructorIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
