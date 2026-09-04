package com.adn.dabaeum.attendance.application;

import java.util.UUID;

@FunctionalInterface
public interface AttendanceIdGenerator {

    UUID generate();
}
