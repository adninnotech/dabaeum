package com.adn.dabaeum.attendance.application;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class RandomAttendanceIdGenerator implements AttendanceIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
