package com.adn.dabaeum.classification.domain;

import java.util.List;

public interface CourseClassificationRepository {

    /** 단계·정렬 순서대로 전부. 77행이라 매번 읽어도 된다. */
    List<CourseClassification> findAll();

    boolean updateEnabled(String code, boolean enabled);
}
