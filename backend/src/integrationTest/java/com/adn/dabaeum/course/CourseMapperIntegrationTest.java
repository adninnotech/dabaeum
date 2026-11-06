package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.KeywordQuery;
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
    void softDeleteDraftHidesOnlyActiveDraftCourse() {
        Course draft = course("COURSE-DEL-DRAFT", "임시저장 삭제 과정");
        repository.save(draft);
        Instant deletedAt = Instant.parse("2099-08-03T00:00:00Z");

        assertThat(repository.softDeleteDraft(draft.id(), deletedAt)).isTrue();
        assertThat(repository.findActiveById(draft.id())).isEmpty();
        assertThat(repository.findById(draft.id())).get()
            .extracting(Course::deletedAt).isEqualTo(deletedAt);
        // 이미 지운 강좌는 다시 지워지지 않는다
        assertThat(repository.softDeleteDraft(draft.id(), deletedAt)).isFalse();

        Course published = course("COURSE-DEL-PUB", "게시 과정");
        repository.save(published);
        jdbcTemplate.update("UPDATE tb_courses SET status = 'RECRUITING' WHERE id = ?", published.id());
        assertThat(repository.softDeleteDraft(published.id(), deletedAt)).isFalse();
        assertThat(repository.findActiveById(published.id())).isPresent();
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
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, null, null, null, null,
            true, null));

        assertThat(page).extracting(Course::id)
            .contains(newer.id())
            .doesNotContain(older.id());
    }

    @Test
    void keywordSearchMatchesTitleAndExcludesUnrelatedCourse() {
        Course target = course("COURSE-KEYWORD", "키워드검색전용과정" + UUID.randomUUID());
        Course other = course("COURSE-OTHER", "관련없는과정" + UUID.randomUUID());
        String keyword = KeywordQuery.normalize(target.title().substring(0, 8));
        long before = repository.countActive(keyword, null, null, null, true, null, null);

        repository.save(target);
        repository.save(other);

        List<Course> matched = repository.findActivePage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword, null, null, null,
            true, null));
        assertThat(matched).extracting(Course::id)
            .contains(target.id())
            .doesNotContain(other.id());

        // countActive 에도 같은 keyword 필터가 걸려야 한다 — target 하나만큼만 늘어난다.
        assertThat(repository.countActive(keyword, null, null, null, true, null, null))
            .isEqualTo(before + 1);
    }

    @Test
    void filtersByEducationTypeAndSelectionMethodWithMatchingCount() {
        // course() 는 항상 HYBRID·FCFS(CourseDetail.empty() 기본값)로 만든다.
        // 공유 dev DB 라 절대 개수는 보지 않고, 고유 키워드로 이 테스트가 만든 행만 본다.
        Course course = course("COURSE-FILTER", "필터전용과정" + UUID.randomUUID());
        String keyword = KeywordQuery.normalize(course.title().substring(0, 8));
        repository.save(course);

        List<Course> hybridMatch = repository.findActivePage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword,
            CourseEducationType.HYBRID, com.adn.dabaeum.course.domain.CourseSelectionMethod.FCFS,
            CourseStatus.DRAFT, true, null));
        List<Course> onlineMiss = repository.findActivePage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword,
            CourseEducationType.ONLINE, null, null, true, null));
        List<Course> wrongStatusMiss = repository.findActivePage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword,
            null, null, CourseStatus.CANCELLED, true, null));

        assertThat(hybridMatch).extracting(Course::id).contains(course.id());
        assertThat(onlineMiss).extracting(Course::id).doesNotContain(course.id());
        assertThat(wrongStatusMiss).extracting(Course::id).doesNotContain(course.id());

        // countActive 도 같은 조건이어야 한다 — 같은 키워드로 좁히면 이 과정 하나뿐이다.
        assertThat(repository.countActive(keyword, CourseEducationType.ONLINE, null, null, true, null, null))
            .isZero();
        assertThat(repository.countActive(keyword, null, null, CourseStatus.CANCELLED, true, null, null))
            .isZero();
        assertThat(repository.countActive(
                keyword, CourseEducationType.HYBRID,
                com.adn.dabaeum.course.domain.CourseSelectionMethod.FCFS, CourseStatus.DRAFT,
                true, null, null))
            .isEqualTo(1);
    }

    @Test
    void percentKeywordIsEscapedInsteadOfMatchingEveryTitle() {
        Course withPercent = course("COURSE-PERCENT", "50%특가과정" + UUID.randomUUID());
        Course withoutPercent = course("COURSE-NO-PERCENT", "일반과정" + UUID.randomUUID());
        repository.save(withPercent);
        repository.save(withoutPercent);

        String keyword = KeywordQuery.normalize("%");
        List<Course> matched = repository.findActivePage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword, null, null, null,
            true, null));

        assertThat(matched).extracting(Course::id)
            .contains(withPercent.id())
            .doesNotContain(withoutPercent.id());
    }

    @Test
    void appliesOffsetLimitAndExcludesDeletedRowsFromCount() {
        long baseline = repository.countActive(null, null, null, null, true, null, null);
        Course first = course("COURSE-1", "과정 1");
        Course second = course("COURSE-2", "과정 2");
        Course deleted = course("COURSE-3", "과정 3");
        repository.save(first);
        repository.save(second);
        repository.save(deleted);
        softDelete(deleted.id());

        List<Course> firstTwo = repository.findActivePage(new CoursePageCriteria(
            0, 2, CourseSort.COURSE_CODE, CourseSortDirection.ASC, null, null, null, null,
            true, null));
        List<Course> page = repository.findActivePage(new CoursePageCriteria(
            1, 1, CourseSort.COURSE_CODE, CourseSortDirection.ASC, null, null, null, null,
            true, null));

        assertThat(firstTwo).hasSize(2);
        assertThat(page).containsExactly(firstTwo.get(1));
        assertThat(repository.countActive(null, null, null, null, true, null, null))
            .isEqualTo(baseline + 2);
    }

    /**
     * includeAllDraft·draftVisibleInstitutionIds 가 findActiveViewPage·countActive 양쪽에
     * 같은 기준으로 걸리는지 실제 SQL 로 증명한다. 공유 dev DB 라 고유 키워드로 이 테스트가
     * 만든 DRAFT 강좌 하나만 걸러서 본다.
     */
    @Test
    void DRAFT_가시성_제한이_목록과_건수에_같이_걸린다() {
        Course draft = course("COURSE-DRAFT-VIS", "가시성제한전용과정" + UUID.randomUUID());
        String keyword = KeywordQuery.normalize(draft.title().substring(0, 8));
        repository.save(draft);
        UUID institutionId = draft.institutionId();
        UUID otherInstitutionId = UUID.randomUUID();

        // 제한 없음(PLATFORM_ADMIN) — 보인다.
        assertThat(repository.findActiveViewPage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword, null, null, null,
            true, null)))
            .extracting(view -> view.course().id())
            .contains(draft.id());
        assertThat(repository.countActive(keyword, null, null, null, true, null, null)).isEqualTo(1);

        // 그 기관이 draftVisibleInstitutionIds 에 있으면 보인다.
        assertThat(repository.findActiveViewPage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword, null, null, null,
            false, List.of(institutionId))))
            .extracting(view -> view.course().id())
            .contains(draft.id());
        assertThat(repository.countActive(
                keyword, null, null, null, false, List.of(institutionId), null))
            .isEqualTo(1);

        // 다른 기관만 있으면 안 보인다.
        assertThat(repository.findActiveViewPage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword, null, null, null,
            false, List.of(otherInstitutionId))))
            .extracting(view -> view.course().id())
            .doesNotContain(draft.id());
        assertThat(repository.countActive(
                keyword, null, null, null, false, List.of(otherInstitutionId), null))
            .isZero();

        // 제한 명단이 비어 있으면 안 보인다 — status=DRAFT 를 명시해도 마찬가지다.
        assertThat(repository.findActiveViewPage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, keyword, null, null,
            CourseStatus.DRAFT, false, null)))
            .extracting(view -> view.course().id())
            .doesNotContain(draft.id());
        assertThat(repository.countActive(
                keyword, null, null, CourseStatus.DRAFT, false, null, null))
            .isZero();
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
            "TSIX_050303",
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
            "TSIX_040201",
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
