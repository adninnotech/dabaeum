package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
import com.adn.dabaeum.course.domain.CourseStatus;

public record ListCoursesQuery(
    int page,
    int size,
    String sort,
    String keyword,
    CourseEducationType educationType,
    CourseSelectionMethod selectionMethod,
    CourseStatus status,
    /** 표준 분류 코드. 어느 단계든 하위 포함으로 거른다(설계서 §12.3). */
    String classificationCode
) {

    public ListCoursesQuery(
        int page, int size, String sort, String keyword,
        CourseEducationType educationType, CourseSelectionMethod selectionMethod, CourseStatus status
    ) {
        this(page, size, sort, keyword, educationType, selectionMethod, status, null);
    }
}
