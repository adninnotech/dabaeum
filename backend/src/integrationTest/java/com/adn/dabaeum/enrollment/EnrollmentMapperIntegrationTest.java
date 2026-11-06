package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentSort;
import com.adn.dabaeum.enrollment.domain.EnrollmentSortDirection;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;

class EnrollmentMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentRepository repository;
    @Autowired EnrollmentQueryRepository queryRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    /**
     * 수강현황관리(admin) 목록의 키워드 검색. 과정명이 고유해야 이 행 하나만 걸린다는 것을
     * 확인할 수 있다 — 공용 course() 헬퍼의 제목은 고정값이라 다른 테스트 행과 겹칠 수 있다.
     */
    @Test
    void adminEnrollmentKeywordMatchesInsertedRowAndCountAgrees() {
        String uniqueTitle = uniqueCode("검색전용과정");
        Course course = course(uniqueTitle);
        User user = user();
        courseRepository.save(course);
        userRepository.save(user);
        repository.save(enrollment(course.id(), user.id(), null, EnrollmentStatus.APPLIED));

        String keyword = KeywordQuery.normalize(uniqueTitle);

        List<InstitutionEnrollmentView> matches = queryRepository.findAllEnrollments(
            null, null, null, null, keyword, 10, 0, "appliedAt,desc");

        assertThat(matches).extracting(InstitutionEnrollmentView::courseId)
            .containsExactly(course.id());
        assertThat(matches).extracting(InstitutionEnrollmentView::institutionName)
            .containsExactly("수강 테스트 기관");
        assertThat(queryRepository.countAllEnrollments(null, null, null, null, keyword))
            .isEqualTo(1);
    }

    @Test
    void roundTripsNullableFieldsAndExpectedStatusUpdate() {
        Course course = course();
        User user = user();
        courseRepository.save(course);
        userRepository.save(user);
        Enrollment applied = enrollment(course.id(), user.id(), null, EnrollmentStatus.APPLIED);

        repository.save(applied);

        assertThat(repository.findActiveByCourseIdAndUserId(course.id(), user.id()))
            .contains(applied);
        assertThat(repository.countApprovedByCourseId(course.id())).isZero();
        assertThat(repository.findByIdForUpdate(applied.id())).contains(applied);

        Enrollment approved = new Enrollment(
            applied.id(), applied.courseId(), applied.userId(), applied.appliedBy(),
            applied.applicationType(), EnrollmentStatus.APPROVED, applied.appliedAt(),
            applied.appliedAt().plusSeconds(60), null, null, null, null, null,
            applied.createdAt(), applied.updatedAt().plusSeconds(60));
        assertThat(repository.updateState(approved, EnrollmentStatus.APPLIED)).isTrue();
        assertThat(repository.countApprovedByCourseId(course.id())).isEqualTo(1);
        Enrollment withdrawn = new Enrollment(
            approved.id(), approved.courseId(), approved.userId(), approved.appliedBy(),
            approved.applicationType(), EnrollmentStatus.WITHDRAWN, approved.appliedAt(),
            approved.approvedAt(), null, null, approved.approvedAt().plusSeconds(60),
            null, null, approved.createdAt(), approved.updatedAt().plusSeconds(120));
        assertThat(repository.updateState(withdrawn, EnrollmentStatus.APPLIED)).isFalse();
    }

    @Test
    void enforcesActiveDuplicate() throws SQLException {
        Course course = course();
        User user = user();
        courseRepository.save(course);
        userRepository.save(user);
        Enrollment applied = enrollment(course.id(), user.id(), null, EnrollmentStatus.APPLIED);
        repository.save(applied);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        Savepoint savepoint = connection.setSavepoint();
        assertSqlState("23505", () -> repository.save(
            enrollment(course.id(), user.id(), null, EnrollmentStatus.WAITLISTED)));
        connection.rollback(savepoint);
    }

    @Test
    void allowsTerminalResubmissionAfterRejection() {
        Course course = course();
        User user = user();
        courseRepository.save(course);
        userRepository.save(user);
        Enrollment applied = enrollment(course.id(), user.id(), null, EnrollmentStatus.APPLIED);
        repository.save(applied);

        Enrollment rejected = new Enrollment(
            applied.id(), course.id(), user.id(), null, EnrollmentApplicationType.SELF,
            EnrollmentStatus.REJECTED, applied.appliedAt(), null,
            applied.appliedAt().plusSeconds(30), null, null, "사유", null,
            applied.createdAt(), applied.updatedAt().plusSeconds(30));
        assertThat(repository.findById(applied.id())).contains(applied);
        assertThat(repository.updateState(rejected, EnrollmentStatus.APPLIED)).isTrue();
        Enrollment resubmission = enrollment(course.id(), user.id(), null, EnrollmentStatus.APPLIED);
        repository.save(resubmission);
        assertThat(repository.findActiveByCourseIdAndUserId(course.id(), user.id()))
            .contains(resubmission);
    }

    @Test
    void pagesByCourseAndCountsApprovedRows() {
        Course course = course();
        User first = user();
        User second = user();
        courseRepository.save(course);
        userRepository.save(first);
        userRepository.save(second);
        repository.save(enrollment(course.id(), first.id(), null, EnrollmentStatus.APPLIED));
        Enrollment approved = enrollment(course.id(), second.id(), null, EnrollmentStatus.APPLIED);
        repository.save(approved);
        Enrollment approvedState = new Enrollment(
            approved.id(), approved.courseId(), approved.userId(), approved.appliedBy(),
            approved.applicationType(), EnrollmentStatus.APPROVED, approved.appliedAt(),
            approved.appliedAt().plusSeconds(60), null, null, null, null, null,
            approved.createdAt(), approved.updatedAt().plusSeconds(60));
        repository.updateState(approvedState, EnrollmentStatus.APPLIED);

        List<EnrollmentListItem> page = repository.findEnrollmentListByCourseId(
            new EnrollmentPageCriteria(
                course.id(), 0, 10, EnrollmentSort.USER_ID, EnrollmentSortDirection.ASC));
        assertThat(page).hasSize(2);
        assertThat(page).extracting(item -> item.profile().userName())
            .containsExactly(first.name(), second.name());
        assertThat(repository.countByCourseId(course.id())).isEqualTo(2);
        assertThat(repository.countApprovedByCourseId(course.id())).isEqualTo(1);
    }

    @Test
    void waitlistedRowsGetSequentialPositionsInApplicationOrder() {
        Course course = course();
        User first = user();
        User second = user();
        User third = user();
        courseRepository.save(course);
        userRepository.save(first);
        userRepository.save(second);
        userRepository.save(third);
        Instant base = Instant.parse("2099-08-01T00:00:00Z");
        repository.save(waitlisted(course.id(), first.id(), base));
        repository.save(waitlisted(course.id(), second.id(), base.plusSeconds(60)));
        // 승인 건의 신청 시각을 대기자들보다 뒤로 둔다. 헬퍼의 기본 시각이 base 와 같아
        // APPLIED_AT 정렬이 동률이 되고, 그러면 행 순서를 DB 가 임의로 정해 결과가 흔들린다.
        Enrollment approved = new Enrollment(
            UUID.randomUUID(), course.id(), third.id(), null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED,
            base.plusSeconds(120), base.plusSeconds(120), null, null, null, null, null,
            base.plusSeconds(120), base.plusSeconds(120));
        repository.save(approved);

        List<EnrollmentListItem> page = repository.findEnrollmentListByCourseId(
            new EnrollmentPageCriteria(
                course.id(), 0, 10, EnrollmentSort.APPLIED_AT, EnrollmentSortDirection.ASC));

        assertThat(page).extracting(EnrollmentListItem::waitlistPosition)
            .containsExactly(1, 2, null);
    }

    private Enrollment waitlisted(UUID courseId, UUID userId, Instant appliedAt) {
        return new Enrollment(UUID.randomUUID(), courseId, userId, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.WAITLISTED, appliedAt, null, null,
            null, null, null, null, appliedAt, appliedAt);
    }

    @Test
    void mapsInstitutionIdsToUuidForAUserWithEnrollments() {
        // MyBatis 에는 java.util.UUID 용 TypeHandler 가 없어, resultType 으로 직접 쓰면
        // POJO 로 간주해 private UUID(byte[]) 생성자를 리플렉션 호출하려다 JDK 16+ 의
        // 모듈 캡슐화에 막힌다. 이 결함은 매핑할 행이 있어야 드러나므로, 데이터를 넣지 않는
        // 스모크 테스트로는 잡히지 않는다. 실제 수강신청을 만들어 반환값까지 확인한다.
        Course course = course();
        User user = user();
        courseRepository.save(course);
        userRepository.save(user);
        repository.save(enrollment(course.id(), user.id(), null, EnrollmentStatus.APPROVED));

        List<UUID> institutionIds = repository.findInstitutionIdsByUserId(user.id());

        assertThat(institutionIds).containsExactly(course.institutionId());
    }

    @Test
    void returnsEmptyInstitutionIdsForAUserWithoutEnrollments() {
        User user = user();
        userRepository.save(user);

        assertThat(repository.findInstitutionIdsByUserId(user.id())).isEmpty();
    }

    @Test
    void 과정의_살아있는_수강만_신청순으로_돌려준다() {
        Course course = course();
        User applied = user();
        User approved = user();
        User rejected = user();
        courseRepository.save(course);
        userRepository.save(applied);
        userRepository.save(approved);
        userRepository.save(rejected);
        // 신청 시각이 같으면 정렬이 id(무작위 UUID)로 갈려 순서가 매번 달라진다. 신청 순서를 시각으로 못박는다.
        Instant base = Instant.parse("2099-08-01T00:00:00Z");
        Enrollment first = enrollment(course.id(), applied.id(), null, EnrollmentStatus.APPLIED, base);
        repository.save(first);
        Enrollment second = enrollment(course.id(), approved.id(), null, EnrollmentStatus.APPLIED,
            base.plusSeconds(60));
        repository.save(second);
        repository.updateState(new Enrollment(second.id(), second.courseId(), second.userId(), null,
            second.applicationType(), EnrollmentStatus.APPROVED, second.appliedAt(),
            second.appliedAt().plusSeconds(1), null, null, null, null, null,
            second.createdAt(), second.appliedAt().plusSeconds(1)), EnrollmentStatus.APPLIED);
        Enrollment third = enrollment(course.id(), rejected.id(), null, EnrollmentStatus.APPLIED,
            base.plusSeconds(120));
        repository.save(third);
        repository.updateState(new Enrollment(third.id(), third.courseId(), third.userId(), null,
            third.applicationType(), EnrollmentStatus.REJECTED, third.appliedAt(), null,
            third.appliedAt().plusSeconds(1), null, null, "정원 초과", null,
            third.createdAt(), third.appliedAt().plusSeconds(1)), EnrollmentStatus.APPLIED);

        List<Enrollment> active = repository.findActiveByCourseId(course.id());

        assertThat(active).extracting(Enrollment::id).containsExactly(first.id(), second.id());
        assertThat(active).extracting(Enrollment::status)
            .containsExactly(EnrollmentStatus.APPLIED, EnrollmentStatus.APPROVED);
    }

    private Enrollment enrollment(UUID courseId, UUID userId, UUID appliedBy, EnrollmentStatus status) {
        return enrollment(courseId, userId, appliedBy, status, Instant.parse("2099-08-01T00:00:00Z"));
    }

    private Enrollment enrollment(
        UUID courseId, UUID userId, UUID appliedBy, EnrollmentStatus status, Instant appliedAt
    ) {
        // APPROVED 는 도메인 불변식상 approvedAt 이 있어야 한다. 그 외 상태는 전이 시각이 없어야 한다.
        Instant approvedAt = status == EnrollmentStatus.APPROVED ? appliedAt : null;
        return new Enrollment(UUID.randomUUID(), courseId, userId, appliedBy,
            EnrollmentApplicationType.SELF, status, appliedAt, approvedAt, null, null, null,
            null, null, appliedAt, appliedAt);
    }

    private Course course() {
        return course("수강 과정");
    }

    private Course course(String title) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(UUID.randomUUID(), uniqueCode("INST"),
            "수강 테스트 기관", null, null, null, null, null, InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        return new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"), title,
            null, null, CourseEducationType.HYBRID, LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1), null, null, 10, null, null, false, null,
            CourseStatus.RECRUITING, now, now, null);
    }

    private User user() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, "수강생", "learner-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }
}
