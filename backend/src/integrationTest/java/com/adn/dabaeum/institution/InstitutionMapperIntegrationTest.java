package com.adn.dabaeum.institution;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.application.CourseQueryService;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionSort;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.institution.domain.SortDirection;
import com.adn.dabaeum.role.application.AssignRoleCommand;
import com.adn.dabaeum.role.application.RoleApplicationService;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InstitutionMapperIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    InstitutionRepository repository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    DashboardRepository dashboardRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    RoleApplicationService roleApplicationService;

    @Autowired
    CourseQueryService courseQueryService;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Test
    void savesAndFindsAllNullableFields() {
        Institution institution = institution(
            InstitutionStatus.ACTIVE,
            "다배움 전체 필드 기관",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            null
        );

        repository.save(institution);

        assertThat(repository.findById(institution.id()))
            .contains(institution);
    }

    @Test
    void findActiveByIdAndCodeExcludeSoftDeletedRow() {
        Institution institution = institution(
            InstitutionStatus.ACTIVE,
            "다배움 삭제 기관",
            Instant.now().minus(2, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS),
            null
        );

        repository.save(institution);

        assertThat(repository.findActiveById(institution.id()))
            .contains(institution);
        assertThat(repository.findActiveByCode(institution.institutionCode()))
            .contains(institution);

        softDelete(institution.id());

        assertThat(repository.findById(institution.id()))
            .isPresent()
            .get()
            .extracting(Institution::deletedAt)
            .isNotNull();
        assertThat(repository.findActiveById(institution.id())).isEmpty();
        assertThat(repository.findActiveByCode(institution.institutionCode()))
            .isEmpty();
    }

    @Test
    void activePageIncludesAllStatusesAndExcludesDeletedRows() {
        Institution active = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 ACTIVE",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        Institution inactive = institution(
            InstitutionStatus.INACTIVE,
            "기관 페이지 INACTIVE",
            Instant.parse("2026-08-02T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            null
        );
        Institution suspended = institution(
            InstitutionStatus.SUSPENDED,
            "기관 페이지 SUSPENDED",
            Instant.parse("2026-08-03T00:00:00Z"),
            Instant.parse("2026-08-03T00:00:00Z"),
            null
        );
        Institution deleted = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 DELETED",
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:00Z")
        );

        List.of(active, inactive, suspended, deleted)
            .forEach(repository::save);

        List<InstitutionListItem> page = repository.findActivePage(new InstitutionPageCriteria(
            0,
            100,
            InstitutionSort.INSTITUTION_CODE,
            SortDirection.ASC,
            null,
            null,
            null
        ));

        assertThat(page)
            .extracting(item -> item.institution().id())
            .contains(active.id(), inactive.id(), suspended.id())
            .doesNotContain(deleted.id());
    }

    @Test
    void keywordSearchMatchesNameRepresentativeOrHyphenlessPhoneAndStatusFilters() {
        // institution() 은 대표자 "홍길동"·연락처 "02-1234-5678" 고정이라 이름만 고유하게 만든다.
        Institution target = institution(
            InstitutionStatus.ACTIVE,
            "기관검색전용" + UUID.randomUUID(),
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(target);
        String nameKeyword = KeywordQuery.normalize(target.name());

        List<InstitutionListItem> byName = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC,
            nameKeyword, null, null));
        List<InstitutionListItem> byRepresentative = repository.findActivePage(
            new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC,
            KeywordQuery.normalize("홍길동"), InstitutionStatus.ACTIVE, null));
        // 화면에서 하이픈을 넣어 검색해도 저장된 하이픈과 무관하게 걸려야 한다.
        List<InstitutionListItem> byHyphenlessPhone = repository.findActivePage(
            new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC,
            KeywordQuery.normalize("0212345678"), null, null));
        List<InstitutionListItem> wrongStatus = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC,
            nameKeyword, InstitutionStatus.SUSPENDED, null));

        assertThat(byName).extracting(item -> item.institution().id()).contains(target.id());
        assertThat(byRepresentative).extracting(item -> item.institution().id())
            .contains(target.id());
        assertThat(byHyphenlessPhone).extracting(item -> item.institution().id())
            .contains(target.id());
        assertThat(wrongStatus).extracting(item -> item.institution().id())
            .doesNotContain(target.id());

        // countActive 에도 같은 조건이 걸려야 한다 — 고유 이름 키워드라 이 기관 하나뿐이다.
        assertThat(repository.countActive(nameKeyword, InstitutionStatus.ACTIVE, null)).isEqualTo(1);
        assertThat(repository.countActive(nameKeyword, InstitutionStatus.SUSPENDED, null)).isZero();
    }

    @Test
    void appliesCreatedAtDescendingAndNameAscendingSorts() {
        Institution older = institution(
            InstitutionStatus.ACTIVE,
            "기관 정렬 Z",
            Instant.parse("2090-08-01T00:00:00Z"),
            Instant.parse("2090-08-01T00:00:00Z"),
            null
        );
        Institution newer = institution(
            InstitutionStatus.ACTIVE,
            "기관 정렬 A",
            Instant.parse("2090-08-02T00:00:00Z"),
            Instant.parse("2090-08-02T00:00:00Z"),
            null
        );
        repository.save(older);
        repository.save(newer);

        List<Institution> createdAtDesc = repository.findActivePage(
            new InstitutionPageCriteria(
                0,
                100,
                InstitutionSort.CREATED_AT,
                SortDirection.DESC,
                null,
                null,
                null
            )
        ).stream().map(InstitutionListItem::institution).toList();
        assertThat(createdAtDesc.indexOf(newer))
            .isLessThan(createdAtDesc.indexOf(older));

        List<Institution> nameAsc = repository.findActivePage(
            new InstitutionPageCriteria(
                0,
                100,
                InstitutionSort.NAME,
                SortDirection.ASC,
                null,
                null,
                null
            )
        ).stream().map(InstitutionListItem::institution).toList();
        assertThat(nameAsc.indexOf(newer))
            .isLessThan(nameAsc.indexOf(older));
    }

    @Test
    void appliesOffsetAndLimit() {
        Institution first = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 1",
            Instant.parse("2091-08-01T00:00:00Z"),
            Instant.parse("2091-08-01T00:00:00Z"),
            null
        );
        Institution second = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 2",
            Instant.parse("2091-08-02T00:00:00Z"),
            Instant.parse("2091-08-02T00:00:00Z"),
            null
        );
        Institution third = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 3",
            Instant.parse("2091-08-03T00:00:00Z"),
            Instant.parse("2091-08-03T00:00:00Z"),
            null
        );
        List.of(first, second, third).forEach(repository::save);

        List<InstitutionListItem> firstTwo = repository.findActivePage(
            new InstitutionPageCriteria(
                0,
                2,
                InstitutionSort.CREATED_AT,
                SortDirection.ASC,
                null,
                null,
                null
            )
        );
        List<InstitutionListItem> page = repository.findActivePage(new InstitutionPageCriteria(
            1,
            1,
            InstitutionSort.CREATED_AT,
            SortDirection.ASC,
            null,
            null,
            null
        ));

        assertThat(firstTwo).hasSize(2);
        assertThat(page).containsExactly(firstTwo.get(1));
    }

    @Test
    void findByIdsReturnsOnlyMatchingRows() {
        Institution first = institution(
            InstitutionStatus.ACTIVE,
            "기관 findByIds 1",
            Instant.now(),
            Instant.now(),
            null
        );
        Institution second = institution(
            InstitutionStatus.ACTIVE,
            "기관 findByIds 2",
            Instant.now(),
            Instant.now(),
            null
        );
        repository.save(first);
        repository.save(second);

        List<Institution> found = repository.findByIds(
            List.of(first.id(), second.id(), UUID.randomUUID()));

        assertThat(found)
            .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void countActiveExcludesDeletedRows() {
        long baseline = repository.countActive(null, null, null);
        Institution active = institution(
            InstitutionStatus.ACTIVE,
            "기관 count active",
            Instant.now(),
            Instant.now(),
            null
        );
        Institution deleted = institution(
            InstitutionStatus.ACTIVE,
            "기관 count deleted",
            Instant.now(),
            Instant.now(),
            Instant.now()
        );

        repository.save(active);
        repository.save(deleted);

        assertThat(repository.countActive(null, null, null)).isEqualTo(baseline + 1);
    }

    @Test
    void updatesAllPublicFieldsAndUpdatedAt() {
        Institution original = institution(
            InstitutionStatus.ACTIVE,
            "기관 수정 전",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(original);

        Institution updated = new Institution(
            original.id(),
            original.institutionCode() + "-UPDATED",
            "기관 수정 후",
            "987-65-43210",
            "김수정",
            "부산시 해운대구",
            "051-9876-5432",
            "updated@example.com",
            InstitutionStatus.SUSPENDED,
            original.createdAt(),
            Instant.parse("2026-08-03T00:00:00Z"),
            null
        );

        assertThat(repository.updateActive(updated)).isTrue();
        assertThat(repository.findById(updated.id())).contains(updated);
    }

    @Test
    void updateActiveReturnsFalseForDeletedRow() {
        Institution original = institution(
            InstitutionStatus.ACTIVE,
            "기관 삭제 수정",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(original);
        softDelete(original.id());

        Institution updated = new Institution(
            original.id(),
            original.institutionCode(),
            "수정 불가",
            original.businessNumber(),
            original.representativeName(),
            original.address(),
            original.contactPhone(),
            original.contactEmail(),
            InstitutionStatus.INACTIVE,
            original.createdAt(),
            Instant.parse("2026-08-04T00:00:00Z"),
            original.deletedAt()
        );

        assertThat(repository.updateActive(updated)).isFalse();
    }

    @Test
    void 홈페이지_지역_로고를_저장하고_그대로_읽는다() {
        Institution institution = new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            "홈페이지·지역 기관",
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            InstitutionStatus.ACTIVE,
            Instant.parse("2026-08-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS),
            Instant.parse("2026-08-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS),
            null,
            "https://example.kr/edu",
            "경기 포천시",
            null
        );

        repository.save(institution);

        assertThat(repository.findById(institution.id()))
            .contains(institution);
    }

    @Test
    void region_필터가_일치하는_기관만_찾고_카운트도_같다() {
        String region = "지역필터전용-" + UUID.randomUUID();
        Institution matching = institution(
            InstitutionStatus.ACTIVE,
            "지역 필터 일치",
            Instant.now(),
            Instant.now(),
            null
        );
        matching = new Institution(
            matching.id(), matching.institutionCode(), matching.name(),
            matching.businessNumber(), matching.representativeName(), matching.address(),
            matching.contactPhone(), matching.contactEmail(), matching.status(),
            matching.createdAt(), matching.updatedAt(), matching.deletedAt(),
            null, region, null
        );
        Institution other = institution(
            InstitutionStatus.ACTIVE,
            "지역 필터 불일치",
            Instant.now(),
            Instant.now(),
            null
        );
        repository.save(matching);
        repository.save(other);

        List<InstitutionListItem> found = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC,
            null, null, region));

        assertThat(found).extracting(item -> item.institution().id())
            .containsExactly(matching.id());
        assertThat(repository.countActive(null, null, region)).isEqualTo(1);
    }

    @Test
    void 대시보드가_기관의_카테고리별_강좌_수를_센다() {
        UUID institutionId = UUID.randomUUID();
        Institution owner = institution(
            InstitutionStatus.ACTIVE,
            "대시보드 카테고리 기관",
            Instant.now(),
            Instant.now(),
            null
        );
        owner = new Institution(
            institutionId, owner.institutionCode(), owner.name(), owner.businessNumber(),
            owner.representativeName(), owner.address(), owner.contactPhone(),
            owner.contactEmail(), owner.status(), owner.createdAt(), owner.updatedAt(),
            owner.deletedAt(), owner.homepageUrl(), owner.region(), owner.logoFileId());
        repository.save(owner);

        courseRepository.save(course(institutionId, "TSIX_050302"));
        courseRepository.save(course(institutionId, "TSIX_050302"));
        courseRepository.save(course(institutionId, "TSIX_010201"));

        InstitutionDashboard dashboard = dashboardRepository.institutionDashboard(institutionId);

        assertThat(dashboard.categoryRatios()).containsExactly(
            // 표준 분류의 1단계 이름으로 묶는다(설계서 §12, 계획 3 확정 3).
            new InstitutionDashboard.CategoryRatio("인문교양교육", 2, 67),
            new InstitutionDashboard.CategoryRatio("기초문해교육", 1, 33));
    }

    @Test
    void learnerCount은_같은_학습자가_두_과정을_들어도_1로_센다() {
        UUID institutionId = UUID.randomUUID();
        Institution owner = institutionWithId(institutionId, "중복 방지 기관");
        repository.save(owner);
        Course first = course(institutionId, "TSIX_010201");
        Course second = course(institutionId, "TSIX_010201");
        courseRepository.save(first);
        courseRepository.save(second);
        User learner = user();
        userRepository.save(learner);
        enrollmentRepository.save(
            enrollment(first.id(), learner.id(), EnrollmentStatus.APPROVED));
        enrollmentRepository.save(
            enrollment(second.id(), learner.id(), EnrollmentStatus.APPROVED));

        List<InstitutionListItem> page = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC, null, null, null));

        InstitutionListItem found = page.stream()
            .filter(row -> row.institution().id().equals(institutionId))
            .findFirst()
            .orElseThrow();
        assertThat(found.learnerCount()).isEqualTo(1L);
    }

    // 상세 조회(findActiveById)는 Institution 을 돌려주고, Institution 은 집계 3종을
    // 필드로 아예 갖지 않는다 — "상세에는 없다"가 런타임 null 이 아니라 타입으로 보장된다.
    @Test
    void 집계_3종이_없으면_목록에서_0이다() {
        Institution owner = institution(
            InstitutionStatus.ACTIVE, "집계 없음 기관",
            Instant.now(), Instant.now(), null);
        repository.save(owner);

        List<InstitutionListItem> page = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC, null, null, null));
        InstitutionListItem found = page.stream()
            .filter(row -> row.institution().id().equals(owner.id()))
            .findFirst()
            .orElseThrow();

        assertThat(found.instructorCount()).isEqualTo(0L);
        assertThat(found.courseCount()).isEqualTo(0L);
        assertThat(found.learnerCount()).isEqualTo(0L);
        assertThat(found.adminCount()).isEqualTo(0L);
        assertThat(found.primaryAdminName()).isNull();
    }

    @Test
    void adminCount와_대표관리자가_상세의_administrators와_같고_강사는_세지_않는다() {
        UUID institutionId = UUID.randomUUID();
        Institution owner = institutionWithId(institutionId, "관리자수 일치 기관");
        repository.save(owner);
        User firstAdmin = namedUser("김다혜");
        User secondAdmin = namedUser("이관리");
        User instructor = namedUser("박강사");
        userRepository.save(firstAdmin);
        userRepository.save(secondAdmin);
        userRepository.save(instructor);
        roleApplicationService.assign(
            new AssignRoleCommand(firstAdmin.id(), UserRole.INSTITUTION_ADMIN, institutionId));
        roleApplicationService.assign(
            new AssignRoleCommand(secondAdmin.id(), UserRole.INSTITUTION_ADMIN, institutionId));
        roleApplicationService.assign(
            new AssignRoleCommand(instructor.id(), UserRole.INSTRUCTOR, institutionId));

        List<InstitutionListItem> page = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC, null, null, null));
        InstitutionListItem found = page.stream()
            .filter(row -> row.institution().id().equals(institutionId))
            .findFirst()
            .orElseThrow();

        assertThat(found.adminCount()).isEqualTo(2L);
        var administrators = userRoleRepository.findInstitutionAdministrators(institutionId);
        assertThat(found.adminCount()).isEqualTo(administrators.size());
        // "김다혜 외 1명" — 대표는 상세 administrators 의 첫 번째(가장 먼저 배정된 관리자)다.
        assertThat(found.primaryAdminName()).isEqualTo(administrators.get(0).name());
        assertThat(found.primaryAdminName()).isIn("김다혜", "이관리");
    }

    @Test
    void instructorCount이_강사목록_totalElements와_같다() {
        UUID institutionId = UUID.randomUUID();
        Institution owner = institutionWithId(institutionId, "강사수 일치 기관");
        repository.save(owner);
        User instructor = user();
        userRepository.save(instructor);
        roleApplicationService.assign(
            new AssignRoleCommand(instructor.id(), UserRole.INSTRUCTOR, institutionId));

        List<InstitutionListItem> page = repository.findActivePage(new InstitutionPageCriteria(
            0, 100, InstitutionSort.INSTITUTION_CODE, SortDirection.ASC, null, null, null));
        InstitutionListItem found = page.stream()
            .filter(row -> row.institution().id().equals(institutionId))
            .findFirst()
            .orElseThrow();

        var instructorsPage = courseQueryService.listInstitutionInstructors(
            platformAdmin(), institutionId, null, null, 0, 20, "joinedAt,desc");

        assertThat(found.instructorCount())
            .isEqualTo(instructorsPage.totalElements());
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(
            UUID.randomUUID(), "LOCAL", Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }

    private Institution institutionWithId(UUID institutionId, String name) {
        Institution base = institution(
            InstitutionStatus.ACTIVE, name, Instant.now(), Instant.now(), null);
        return new Institution(
            institutionId, base.institutionCode(), base.name(), base.businessNumber(),
            base.representativeName(), base.address(), base.contactPhone(),
            base.contactEmail(), base.status(), base.createdAt(), base.updatedAt(),
            base.deletedAt(), base.homepageUrl(), base.region(), base.logoFileId());
    }

    private Enrollment enrollment(UUID courseId, UUID userId, EnrollmentStatus status) {
        Instant appliedAt = Instant.parse("2099-08-01T00:00:00Z");
        Instant approvedAt = status == EnrollmentStatus.APPROVED ? appliedAt : null;
        return new Enrollment(UUID.randomUUID(), courseId, userId, null,
            EnrollmentApplicationType.SELF, status, appliedAt, approvedAt, null, null, null,
            null, null, appliedAt, appliedAt);
    }

    private User namedUser(String name) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, name, "admin-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private User user() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, "집계 테스트 학습자", "aggregate-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private Course course(UUID institutionId, String classificationCode) {
        Instant now = Instant.now();
        return new Course(
            UUID.randomUUID(), institutionId, uniqueCode("COURSE"), "카테고리 테스트 과정",
            null, classificationCode, CourseEducationType.ONLINE, null, null, null, null, null,
            null, null, false, null, CourseStatus.DRAFT, now, now, null);
    }

    private Institution institution(
        InstitutionStatus status,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        return new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            name,
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            status,
            createdAt.truncatedTo(ChronoUnit.MICROS),
            updatedAt.truncatedTo(ChronoUnit.MICROS),
            deletedAt == null ? null : deletedAt.truncatedTo(ChronoUnit.MICROS)
        );
    }

    private void softDelete(UUID id) {
        int updated = jdbcTemplate.update(
            """
            UPDATE tb_institutions
               SET deleted_at = CURRENT_TIMESTAMP
             WHERE id = ?
            """,
            id
        );
        assertThat(updated).isEqualTo(1);
    }
}
