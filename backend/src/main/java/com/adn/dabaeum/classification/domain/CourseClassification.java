package com.adn.dabaeum.classification.domain;

/** 표준 강좌 분류 코드 하나 (설계서 §12). 1단계는 parentCode 가 없다. */
public record CourseClassification(
    String code,
    int level,
    String parentCode,
    String name,
    int sortOrder,
    boolean enabled
) {
}
