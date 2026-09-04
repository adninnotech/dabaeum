package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentSort;
import com.adn.dabaeum.enrollment.domain.EnrollmentSortDirection;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
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
    @Autowired CourseRepository courseRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

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

        List<Enrollment> page = repository.findPageByCourseId(new EnrollmentPageCriteria(
            course.id(), 0, 10, EnrollmentSort.USER_ID, EnrollmentSortDirection.ASC));
        assertThat(page).hasSize(2);
        assertThat(repository.countByCourseId(course.id())).isEqualTo(2);
        assertThat(repository.countApprovedByCourseId(course.id())).isEqualTo(1);
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

    private Enrollment enrollment(UUID courseId, UUID userId, UUID appliedBy, EnrollmentStatus status) {
        Instant appliedAt = Instant.parse("2099-08-01T00:00:00Z");
        // APPROVED 는 도메인 불변식상 approvedAt 이 있어야 한다. 그 외 상태는 전이 시각이 없어야 한다.
        Instant approvedAt = status == EnrollmentStatus.APPROVED ? appliedAt : null;
        return new Enrollment(UUID.randomUUID(), courseId, userId, appliedBy,
            EnrollmentApplicationType.SELF, status, appliedAt, approvedAt, null, null, null,
            null, null, appliedAt, appliedAt);
    }

    private Course course() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(UUID.randomUUID(), uniqueCode("INST"),
            "수강 테스트 기관", null, null, null, null, null, InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        return new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"), "수강 과정",
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
