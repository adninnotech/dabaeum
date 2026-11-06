package com.adn.dabaeum.enrollment.domain;

import com.adn.dabaeum.course.domain.CourseFeeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LearningCourseView(
    UUID enrollmentId,
    UUID courseId,
    String title,
    String institutionName,
    String learningStatus,
    String educationType,
    LocalDate startDate,
    LocalDate endDate,
    Instant appliedAt,
    String instructorName,
    String classificationCode,
    Integer sessionCount,
    String tags,
    CourseFeeType tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId,
    long attendedCount
) {

    /**
     * 전체 회차({@link #sessionCount()}) 대비 출석 비율. {@code /enrollments/{id}/progress}
     * 의 진도율과 분모가 다르다 — 그쪽은 아직 열리지 않은 회차를 뺀 "출결 대상 회차" 기준이고,
     * 여기는 취소되지 않은 전체 회차 기준이다. 목록 카드는 강좌 전체 진행 정도를 보여주는 용도라
     * 이렇게 둔다.
     */
    public int progressPercent() {
        if (sessionCount == null || sessionCount <= 0) {
            return 0;
        }
        long percent = Math.round(attendedCount * 100.0 / sessionCount);
        return (int) Math.min(100, Math.max(0, percent));
    }
}
