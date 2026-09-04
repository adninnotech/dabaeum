package com.adn.dabaeum.course.domain;

public final class CourseSessionStatusPolicy {

    private CourseSessionStatusPolicy() {
    }

    /** 관리자 정정: 잘못 마감한 회차를 다시 연다. 취소된 회차는 되돌리지 않는다. */
    public static CourseSessionStatus reopenTarget(CourseSessionStatus current) {
        if (current != CourseSessionStatus.COMPLETED) {
            throw new IllegalStateException("Course session cannot be reopened");
        }
        return CourseSessionStatus.OPEN;
    }

    public static CourseSessionStatus updateTarget(
        CourseSessionStatus current,
        CourseSessionStatus requested
    ) {
        if (current == null || requested == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (current == requested) {
            return current;
        }
        if (requested == CourseSessionStatus.CANCELLED
            && (current == CourseSessionStatus.SCHEDULED
                || current == CourseSessionStatus.OPEN)) {
            return requested;
        }
        if (current == CourseSessionStatus.SCHEDULED
            && requested == CourseSessionStatus.OPEN) {
            return requested;
        }
        if (current == CourseSessionStatus.OPEN
            && requested == CourseSessionStatus.COMPLETED) {
            return requested;
        }
        throw new IllegalStateException(
            "Invalid course session status transition: " + current + " -> " + requested
        );
    }
}
