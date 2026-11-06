package com.adn.dabaeum.popularity.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PopularCourseScore(UUID courseId, int enrollmentCount, int viewCount, BigDecimal score) {
}
