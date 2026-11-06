package com.adn.dabaeum.course.application;

import java.util.UUID;

public interface CourseCodeGenerator {

    String generate(UUID institutionId);
}
