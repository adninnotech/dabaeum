package com.adn.dabaeum.course.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoursePublishRequirementsTest {

    @Test
    void 일정과_정원이_다_있으면_빠진_것이_없다() {
        Course course = draftCourse(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 21), 20);

        assertThat(CoursePublishRequirements.missingFields(course)).isEmpty();
    }

    @Test
    void 시작일이_없으면_이름을_알려준다() {
        Course course = draftCourse(null, LocalDate.of(2026, 12, 21), 20);

        assertThat(CoursePublishRequirements.missingFields(course))
            .containsExactly("startDate");
    }

    @Test
    void 여러_개가_빠지면_모두_알려준다() {
        Course course = draftCourse(null, null, null);

        assertThat(CoursePublishRequirements.missingFields(course))
            .containsExactly("startDate", "endDate", "capacity");
    }

    @Test
    void 표준_분류가_없으면_이름을_알려준다() {
        Course course = draftCourse(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 10, null);

        assertThat(CoursePublishRequirements.missingFields(course)).containsExactly("classificationCode");
    }

    private Course draftCourse(LocalDate startDate, LocalDate endDate, Integer capacity) {
        return draftCourse(startDate, endDate, capacity, "TSIX_040201");
    }

    private Course draftCourse(LocalDate startDate, LocalDate endDate, Integer capacity, String classificationCode) {
        return new Course(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "COURSE-001",
            "과정 제목",
            null,
            classificationCode,
            CourseEducationType.ONLINE,
            startDate,
            endDate,
            null,
            null,
            capacity,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
    }
}
