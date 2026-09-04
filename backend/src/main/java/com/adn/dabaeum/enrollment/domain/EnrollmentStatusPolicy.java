package com.adn.dabaeum.enrollment.domain;

import java.util.Objects;

public final class EnrollmentStatusPolicy {

    private EnrollmentStatusPolicy() {
    }

    public static EnrollmentStatus approve(EnrollmentStatus current) {
        require(current, EnrollmentStatus.APPLIED, EnrollmentStatus.WAITLISTED);
        return EnrollmentStatus.APPROVED;
    }

    public static EnrollmentStatus reject(EnrollmentStatus current) {
        require(current, EnrollmentStatus.APPLIED, EnrollmentStatus.WAITLISTED);
        return EnrollmentStatus.REJECTED;
    }

    public static EnrollmentStatus cancel(EnrollmentStatus current) {
        require(current, EnrollmentStatus.APPLIED, EnrollmentStatus.WAITLISTED);
        return EnrollmentStatus.CANCELLED;
    }

    public static EnrollmentStatus withdraw(EnrollmentStatus current) {
        require(current, EnrollmentStatus.APPROVED);
        return EnrollmentStatus.WITHDRAWN;
    }

    private static void require(EnrollmentStatus current, EnrollmentStatus... allowed) {
        Objects.requireNonNull(current, "current");
        for (EnrollmentStatus candidate : allowed) {
            if (candidate == current) {
                return;
            }
        }
        throw new IllegalStateException("Enrollment transition is not allowed");
    }
}
