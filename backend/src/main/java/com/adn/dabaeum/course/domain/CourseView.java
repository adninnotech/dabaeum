package com.adn.dabaeum.course.domain;

import java.util.Objects;

/**
 * 목록·상세 읽기 전용 투영. 승인된 신청자 수는 집계라 Course 애그리거트에 넣지 않는다.
 *
 * <p>institutionName·mainInstructorName 은 목록·상세 읽기 조회에서만 채운다. 생성·수정·
 * 상태전환 같은 쓰기 응답과, 강사가 배정되지 않은 과정(mainInstructorName)에서는 null.
 */
public record CourseView(
    Course course,
    long enrolledCount,
    String institutionName,
    String mainInstructorName
) {

    public CourseView {
        Objects.requireNonNull(course, "course");
    }
}
