package com.adn.dabaeum.course.domain;

import java.util.ArrayList;
import java.util.List;

/** DRAFT 를 공개로 올리기 전에 채워져 있어야 하는 것들. */
public final class CoursePublishRequirements {

    private CoursePublishRequirements() {
    }

    public static List<String> missingFields(Course course) {
        List<String> missing = new ArrayList<>();
        if (course.startDate() == null) {
            missing.add("startDate");
        }
        if (course.endDate() == null) {
            missing.add("endDate");
        }
        if (course.capacity() == null) {
            missing.add("capacity");
        }
        // 표준 분류는 게시할 때 필수다(설계서 §12.3). 초안에서는 비어도 된다.
        if (course.classificationCode() == null) {
            missing.add("classificationCode");
        }
        return List.copyOf(missing);
    }
}
