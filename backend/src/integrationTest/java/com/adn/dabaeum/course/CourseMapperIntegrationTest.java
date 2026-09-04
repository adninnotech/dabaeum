package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSort;
import com.adn.dabaeum.course.domain.CourseSortDirection;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseRepository repository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Test
    void roundTripsAllNullableFields() {
        Course course = course("COURSE-ROUNDTRIP", "전체 필드 과정");

        repository.save(course);

        assertThat(repository.findById(course.id())).contains(course);
    }

    @Test
    void activeLookupsExcludeSoftDeletedCourse() {
        Course course = course("COURSE-SOFT-DELETE", "삭제 과정");
        repository.save(course);
        softDelete(course.id());

        assertThat(repository.findById(course.id())).isPresent();
        assertThat(repository.findActiveById(course.id())).isEmpty();
        assertThat(repository.findActiveByInstitutionAndCode(
            course.institutionId(), course.courseCode())).isEmpty();
    }

    @Test
    void findsActiveCourseWithRowLock() {
        Course course = course("COURSE-LOCK", "잠금 과정");
        repository.save(course);

        assertThat(repository.findActiveByIdForUpdate(course.id())).contains(course);
    }

    @Test
    void activePageSortsAndExcludesSoftDeletedRows() {
        Course older = course("COURSE-Z", "과정 Z");
        Course newer = course("COURSE-A", "과정 A");
        repository.save(older);
        repository.save(newer);
        softDelete(older.id());

        List<Course> page = repository.findActivePage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC));

        assertThat(page).extracting(Course::id)
            .contains(newer.id())
            .doesNotContain(older.id());
    }

    @Test
    void appliesOffsetLimitAndExcludesDeletedRowsFromCount() {
        long baseline = repository.countActive();
        Course first = course("COURSE-1", "과정 1");
        Course second = course("COURSE-2", "과정 2");
        Course deleted = course("COURSE-3", "과정 3");
        repository.save(first);
        repository.save(second);
        repository.save(deleted);
        softDelete(deleted.id());

        List<Course> firstTwo = repository.findActivePage(new CoursePageCriteria(
            0, 2, CourseSort.COURSE_CODE, CourseSortDirection.ASC));
        List<Course> page = repository.findActivePage(new CoursePageCriteria(
            1, 1, CourseSort.COURSE_CODE, CourseSortDirection.ASC));

        assertThat(firstTwo).hasSize(2);
        assertThat(page).containsExactly(firstTwo.get(1));
        assertThat(repository.countActive()).isEqualTo(baseline + 2);
    }

    @Test
    void updatesMutableFieldsAndRejectsDeletedRow() {
        Course original = course("COURSE-UPDATE", "수정 전");
        repository.save(original);
        Course updated = new Course(
            original.id(),
            original.institutionId(),
            original.courseCode() + "-V2",
            "수정 후",
            "변경 설명",
            "새 카테고리",
            CourseEducationType.ONLINE,
            original.startDate(),
            original.endDate(),
            original.recruitStartDate(),
            original.recruitEndDate(),
            40,
            "온라인",
            "https://example.test/updated",
            false,
            new BigDecimal("5.00"),
            CourseStatus.RECRUITMENT_CLOSED,
            original.createdAt(),
            original.updatedAt().plus(1, ChronoUnit.DAYS),
            null
        );

        assertThat(repository.updateActive(updated)).isTrue();
        assertThat(repository.findById(original.id())).contains(updated);

        softDelete(original.id());
        assertThat(repository.updateActive(updated)).isFalse();
    }

    @Test
    void activeCourseCodeCanBeReusedAfterSoftDelete() {
        Course first = course("COURSE-REUSE", "첫 과정");
        repository.save(first);
        softDelete(first.id());

        Course second = new Course(
            UUID.randomUUID(),
            first.institutionId(),
            first.courseCode(),
            "두 번째 과정",
            null,
            null,
            CourseEducationType.OFFLINE,
            first.startDate(),
            first.endDate(),
            null,
            null,
            10,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            first.createdAt(),
            first.updatedAt(),
            null
        );

        repository.save(second);

        assertThat(repository.findActiveByInstitutionAndCode(
            first.institutionId(), first.courseCode())).contains(second);
    }

    private Course course(String courseCode, String title) {
        Institution institution = new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            "과정 테스트 기관",
            "123-45-67890",
            "홍길동",
            "서울시",
            "02-1234-5678",
            "course@example.com",
            InstitutionStatus.ACTIVE,
            Instant.parse("2099-08-01T00:00:00Z"),
            Instant.parse("2099-08-01T00:00:00Z"),
            null
        );
        institutionRepository.save(institution);

        return new Course(
            UUID.randomUUID(),
            institution.id(),
            courseCode + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            title,
            "과정 설명",
            "개발",
            CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 31),
            LocalDate.of(2099, 8, 1),
            LocalDate.of(2099, 8, 25),
            20,
            "서울 교육장",
            "https://example.test/course",
            true,
            new BigDecimal("12.50"),
            CourseStatus.DRAFT,
            Instant.parse("2099-08-01T00:00:00Z"),
            Instant.parse("2099-08-02T00:00:00Z"),
            null
        );
    }

    private void softDelete(UUID id) {
        int updated = jdbcTemplate.update(
            "UPDATE tb_courses SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
            id
        );
        assertThat(updated).isEqualTo(1);
    }
}
