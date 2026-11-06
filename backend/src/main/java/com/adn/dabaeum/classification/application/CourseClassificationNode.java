package com.adn.dabaeum.classification.application;

import java.util.List;

/** 분류 트리 노드. 3단계와 하위 없는 1단계는 children 이 비어 있다. */
public record CourseClassificationNode(
    String code,
    int level,
    String name,
    boolean enabled,
    List<CourseClassificationNode> children
) {

    public CourseClassificationNode {
        children = List.copyOf(children);
    }
}
