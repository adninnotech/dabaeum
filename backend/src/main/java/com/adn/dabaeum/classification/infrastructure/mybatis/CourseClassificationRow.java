package com.adn.dabaeum.classification.infrastructure.mybatis;

public record CourseClassificationRow(
    String code,
    int level,
    String parentCode,
    String name,
    int sortOrder,
    boolean enabled
) {
}
