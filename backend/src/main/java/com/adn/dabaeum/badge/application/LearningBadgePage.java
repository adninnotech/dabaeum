package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.badge.domain.LearningBadge;
import java.util.List;

public record LearningBadgePage(
    List<LearningBadge> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
