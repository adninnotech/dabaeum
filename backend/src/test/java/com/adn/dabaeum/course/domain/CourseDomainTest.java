package com.adn.dabaeum.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseDomainTest {

    @Test
    void enforcesCourseLifecycleTransitions() {
        assertThat(CourseStatusPolicy.publishTarget(CourseStatus.DRAFT))
            .isEqualTo(CourseStatus.RECRUITING);
        assertThat(CourseStatusPolicy.closeTarget(CourseStatus.RECRUITING))
            .isEqualTo(CourseStatus.RECRUITMENT_CLOSED);
        assertThat(CourseStatusPolicy.updateTarget(
            CourseStatus.RECRUITMENT_CLOSED,
            CourseStatus.IN_PROGRESS
        )).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(CourseStatusPolicy.updateTarget(
            CourseStatus.IN_PROGRESS,
            CourseStatus.COMPLETED
        )).isEqualTo(CourseStatus.COMPLETED);
        assertThat(CourseStatusPolicy.updateTarget(
            CourseStatus.DRAFT,
            CourseStatus.CANCELLED
        )).isEqualTo(CourseStatus.CANCELLED);

        assertThatThrownBy(() -> CourseStatusPolicy.publishTarget(
            CourseStatus.RECRUITING
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CourseStatusPolicy.closeTarget(
            CourseStatus.DRAFT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CourseStatusPolicy.updateTarget(
            CourseStatus.DRAFT,
            CourseStatus.RECRUITING
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CourseStatusPolicy.updateTarget(
            CourseStatus.COMPLETED,
            CourseStatus.CANCELLED
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preservesAllFieldsIncludingNullableValues() {
        Course course = validCourse(
            "COURSE-001",
            "과정 제목",
            "상세 설명",
            "카테고리",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 25),
            30,
            "서울 교육장",
            "https://example.test/course/1",
            true,
            new BigDecimal("12.50"),
            CourseStatus.RECRUITING,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            null
        );

        assertThat(course).satisfies(value -> {
            assertThat(value.id()).isNotNull();
            assertThat(value.institutionId()).isNotNull();
            assertThat(value.courseCode()).isEqualTo("COURSE-001");
            assertThat(value.title()).isEqualTo("과정 제목");
            assertThat(value.description()).isEqualTo("상세 설명");
            assertThat(value.category()).isEqualTo("카테고리");
            assertThat(value.educationType()).isEqualTo(CourseEducationType.HYBRID);
            assertThat(value.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(value.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(value.recruitStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(value.recruitEndDate()).isEqualTo(LocalDate.of(2026, 8, 25));
            assertThat(value.capacity()).isEqualTo(30);
            assertThat(value.location()).isEqualTo("서울 교육장");
            assertThat(value.onlineUrl()).isEqualTo("https://example.test/course/1");
            assertThat(value.creditBankEligible()).isTrue();
            assertThat(value.creditValue()).isEqualByComparingTo("12.50");
            assertThat(value.status()).isEqualTo(CourseStatus.RECRUITING);
            assertThat(value.createdAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
            assertThat(value.updatedAt()).isEqualTo(Instant.parse("2026-08-02T00:00:00Z"));
            assertThat(value.deletedAt()).isNull();
        });
    }

    @Test
    void allowsNullableCourseFieldsToBeNull() {
        Course course = validCourse(
            "COURSE-NULLABLE",
            "온라인 과정",
            null,
            null,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 1),
            null,
            null,
            1,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );

        assertThat(course.description()).isNull();
        assertThat(course.category()).isNull();
        assertThat(course.recruitStartDate()).isNull();
        assertThat(course.recruitEndDate()).isNull();
        assertThat(course.location()).isNull();
        assertThat(course.onlineUrl()).isNull();
        assertThat(course.creditValue()).isNull();
    }

    @Test
    void rejectsBlankOrOverlongCourseCode() {
        assertThatThrownBy(() -> validCourse(" ", "제목", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validCourse("x".repeat(51), "제목", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankOrOverlongTitle() {
        assertThatThrownBy(() -> validCourse("CODE", " ", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validCourse("CODE", "x".repeat(201), null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidDateRanges() {
        assertThatThrownBy(() -> validCourse("CODE", "제목", null, null,
            LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validCourse("CODE", "제목", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 19),
            1, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCapacityAndCreditOutsideDatabaseRanges() {
        assertThatThrownBy(() -> validCourse("CODE", "제목", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            0, null, null, false, null, CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validCourse("CODE", "제목", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, new BigDecimal("0.00"), CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validCourse("CODE", "제목", null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null,
            1, null, null, false, new BigDecimal("1000.00"), CourseStatus.DRAFT,
            Instant.now(), Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private Course validCourse(
        String courseCode,
        String title,
        String description,
        String category,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate recruitStartDate,
        LocalDate recruitEndDate,
        int capacity,
        String location,
        String onlineUrl,
        boolean creditBankEligible,
        BigDecimal creditValue,
        CourseStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        return new Course(
            UUID.randomUUID(),
            UUID.randomUUID(),
            courseCode,
            title,
            description,
            category,
            CourseEducationType.HYBRID,
            startDate,
            endDate,
            recruitStartDate,
            recruitEndDate,
            capacity,
            location,
            onlineUrl,
            creditBankEligible,
            creditValue,
            status,
            createdAt,
            updatedAt,
            deletedAt
        );
    }
}
