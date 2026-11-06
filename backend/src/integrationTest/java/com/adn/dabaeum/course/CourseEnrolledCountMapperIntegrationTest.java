package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSort;
import com.adn.dabaeum.course.domain.CourseSortDirection;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D-4 미리보기 "3 / 16명" 의 분자가 실제 SQL 로 APPROVED 만 세는지 증명한다.
 * 승인 2건, 신청 1건, 취소 1건이 있는 강좌에서 enrolledCount 는 2 여야 한다.
 */
class CourseEnrolledCountMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CourseRepository courseRepository;
    @Autowired CourseQueryRepository courseQueryRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 승인된_신청만_세고_나머지_강좌를_섞지_않는다() {
        Course course = course();
        courseRepository.save(course);

        enroll(course.id(), EnrollmentStatus.APPROVED);
        enroll(course.id(), EnrollmentStatus.APPROVED);
        enroll(course.id(), EnrollmentStatus.APPLIED);
        enroll(course.id(), EnrollmentStatus.CANCELLED);

        CourseView byId = courseRepository.findActiveViewById(course.id()).orElseThrow();
        assertThat(byId.enrolledCount()).isEqualTo(2);
        assertThat(byId.course()).isEqualTo(course);

        List<CourseView> page = courseRepository.findActiveViewPage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, null, null, null, null,
            true, null));
        CourseView fromPage = page.stream()
            .filter(view -> view.course().id().equals(course.id()))
            .findFirst()
            .orElseThrow();
        assertThat(fromPage.enrolledCount()).isEqualTo(2);
    }

    @Test
    void 신청이_없으면_0이다() {
        Course course = course();
        courseRepository.save(course);

        CourseView view = courseRepository.findActiveViewById(course.id()).orElseThrow();

        assertThat(view.enrolledCount()).isZero();
    }

    /**
     * selectInstitutionCourses(기관 강좌 목록)의 집계 서브쿼리·resultMap 을 실제로 행이
     * 나오는 상태에서 검증한다. 이 문장은 다른 네임스페이스의 resultMap 을 빌려 쓰는데,
     * 빈 결과로만 호출하면 컬럼 목록이 안 맞아도 안 걸린다 — 이 브랜치에서 실제로 그렇게
     * 500 이 난 적이 있다.
     */
    @Test
    void 기관_강좌_목록도_행이_나올_때_집계가_맞는다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course course = course(institution);
        courseRepository.save(course);

        enroll(course.id(), EnrollmentStatus.APPROVED);
        enroll(course.id(), EnrollmentStatus.APPROVED);
        enroll(course.id(), EnrollmentStatus.APPLIED);

        List<CourseView> found = courseQueryRepository.findInstitutionCourses(
            institution.id(), null, 50, 0, "createdAt,desc");

        CourseView view = found.stream()
            .filter(v -> v.course().id().equals(course.id()))
            .findFirst()
            .orElseThrow();
        assertThat(view.enrolledCount()).isEqualTo(2);
    }

    /**
     * 서브쿼리의 상관 조건(en.course_id = tb_courses.id)이 빠지면 강좌 A 의 enrolledCount 에
     * 강좌 B 의 승인 신청까지 섞여 든다. 강좌 두 개를 같은 기관 아래 만들어 서로 다른 수의
     * 승인 신청을 넣고, 각자 자기 것만 세는지 findActiveViewById 와 findActiveViewPage 양쪽에서
     * 확인한다. 공유 dev DB 라 절대값 대신 이 테스트가 만든 두 강좌로 좁혀 비교한다.
     */
    @Test
    void 강좌마다_집계가_따로_돈다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course courseA = course(institution);
        Course courseB = course(institution);
        courseRepository.save(courseA);
        courseRepository.save(courseB);

        enroll(courseA.id(), EnrollmentStatus.APPROVED);
        enroll(courseA.id(), EnrollmentStatus.APPROVED);
        enroll(courseB.id(), EnrollmentStatus.APPROVED);

        assertThat(courseRepository.findActiveViewById(courseA.id()).orElseThrow()
            .enrolledCount()).isEqualTo(2);
        assertThat(courseRepository.findActiveViewById(courseB.id()).orElseThrow()
            .enrolledCount()).isEqualTo(1);

        List<CourseView> page = courseRepository.findActiveViewPage(new CoursePageCriteria(
            0, 100, CourseSort.COURSE_CODE, CourseSortDirection.ASC, null, null, null, null,
            true, null));
        assertThat(enrolledCountOf(page, courseA.id())).isEqualTo(2);
        assertThat(enrolledCountOf(page, courseB.id())).isEqualTo(1);
    }

    private long enrolledCountOf(List<CourseView> views, UUID courseId) {
        return views.stream()
            .filter(view -> view.course().id().equals(courseId))
            .findFirst()
            .orElseThrow()
            .enrolledCount();
    }

    private void enroll(UUID courseId, EnrollmentStatus status) {
        User user = user();
        userRepository.save(user);
        Instant appliedAt = Instant.parse("2099-08-01T00:00:00Z");
        // APPROVED 는 approvedAt, CANCELLED 는 cancelledAt 이 있어야 하는 도메인 불변식.
        Instant approvedAt = status == EnrollmentStatus.APPROVED ? appliedAt : null;
        Instant cancelledAt = status == EnrollmentStatus.CANCELLED ? appliedAt : null;
        enrollmentRepository.save(new Enrollment(
            UUID.randomUUID(), courseId, user.id(), null,
            EnrollmentApplicationType.SELF, status, appliedAt, approvedAt, null, cancelledAt, null,
            null, null, appliedAt, appliedAt));
    }

    private Course course() {
        Institution institution = institution();
        institutionRepository.save(institution);
        return course(institution);
    }

    private Institution institution() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            "집계 테스트 기관",
            "123-45-67890",
            "홍길동",
            "서울시",
            "02-1234-5678",
            "enrolled-count@example.com",
            InstitutionStatus.ACTIVE,
            now,
            now,
            null
        );
    }

    private Course course(Institution institution) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Course(
            UUID.randomUUID(),
            institution.id(),
            uniqueCode("COURSE"),
            "집계 테스트 과정",
            "과정 설명",
            "TSIX_040201",
            CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 31),
            LocalDate.of(2099, 8, 1),
            LocalDate.of(2099, 8, 25),
            16,
            "서울 교육장",
            "https://example.test/course",
            true,
            null,
            CourseStatus.RECRUITING,
            now,
            now,
            null
        );
    }

    private User user() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, "수강생", "learner-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }
}
