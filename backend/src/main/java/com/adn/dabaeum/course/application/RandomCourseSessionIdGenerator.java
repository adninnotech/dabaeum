package com.adn.dabaeum.course.application;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class RandomCourseSessionIdGenerator implements CourseSessionIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
