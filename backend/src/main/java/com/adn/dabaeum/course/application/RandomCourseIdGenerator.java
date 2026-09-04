package com.adn.dabaeum.course.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomCourseIdGenerator implements CourseIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
