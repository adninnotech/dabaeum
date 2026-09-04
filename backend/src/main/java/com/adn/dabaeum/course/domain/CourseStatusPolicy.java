package com.adn.dabaeum.course.domain;

import java.util.Objects;

public final class CourseStatusPolicy {

    private CourseStatusPolicy() {
    }

    public static CourseStatus updateTarget(
        CourseStatus current,
        CourseStatus requested
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        if (current == requested) {
            return current;
        }
        if (requested == CourseStatus.CANCELLED && current != CourseStatus.COMPLETED
            && current != CourseStatus.CANCELLED) {
            return requested;
        }
        if (current == CourseStatus.RECRUITMENT_CLOSED
            && requested == CourseStatus.IN_PROGRESS) {
            return requested;
        }
        if (current == CourseStatus.IN_PROGRESS
            && requested == CourseStatus.COMPLETED) {
            return requested;
        }
        throw new IllegalStateException("Course status transition is not allowed");
    }

    public static CourseStatus publishTarget(CourseStatus current) {
        Objects.requireNonNull(current, "current");
        if (current != CourseStatus.DRAFT) {
            throw new IllegalStateException("Course cannot be published");
        }
        return CourseStatus.RECRUITING;
    }

    /**
     * 관리자 정정: 잘못 마감한 모집을 다시 연다. 종료된 과정(COMPLETED·CANCELLED)은 되돌리지 않는다.
     */
    public static CourseStatus reopenTarget(CourseStatus current) {
        Objects.requireNonNull(current, "current");
        if (current != CourseStatus.RECRUITMENT_CLOSED && current != CourseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Course recruitment cannot be reopened");
        }
        return CourseStatus.RECRUITING;
    }

    public static CourseStatus closeTarget(CourseStatus current) {
        Objects.requireNonNull(current, "current");
        if (current != CourseStatus.RECRUITING) {
            throw new IllegalStateException("Course recruitment cannot be closed");
        }
        return CourseStatus.RECRUITMENT_CLOSED;
    }
}
