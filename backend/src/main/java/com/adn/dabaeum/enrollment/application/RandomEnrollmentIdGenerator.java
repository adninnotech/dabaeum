package com.adn.dabaeum.enrollment.application;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class RandomEnrollmentIdGenerator implements EnrollmentIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
