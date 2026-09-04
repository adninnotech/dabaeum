package com.adn.dabaeum.instructor.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomInstructorApplicationIdGenerator
    implements InstructorApplicationIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
