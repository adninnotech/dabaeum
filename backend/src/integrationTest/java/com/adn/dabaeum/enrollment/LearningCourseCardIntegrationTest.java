package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.LearningCourseView;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 학습 목록 카드 SQL(MAIN 강사 · 회차 수 · 교육비 · 태그 조인)이 실제 DB에서 도는지,
 * 그리고 LEFT JOIN 이 강좌당 여러 행을 만들지 않는지 확인한다. 공유 dev DB 라 이 테스트가
 * 만든 강좌로만 범위를 좁힌다.
 */
class LearningCourseCardIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentQueryRepository enrollmentQueryRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseInstructorRepository courseInstructorRepository;
    @Autowired CourseSessionRepository courseSessionRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void main_강사와_회차가_있는_강좌는_한_행으로_카드_필드를_준다() {
        UUID learnerId = user("학습자");
        Course course = course("건강 교실").withDetail(
            CourseDetail.empty()
                .withTags("실내,체조")
                .withTuition(CourseFeeType.PAID, new BigDecimal("40000")));
        courseRepository.save(course);
        assignMainInstructor(course.id(), "강사입니다");
        addSessions(course.id(), 3);
        enroll(learnerId, course.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, null, 50, 0);

        assertThat(found).filteredOn(v -> v.courseId().equals(course.id())).hasSize(1);
        LearningCourseView view = found.stream()
            .filter(v -> v.courseId().equals(course.id())).findFirst().orElseThrow();
        assertThat(view.instructorName()).isEqualTo("강사입니다");
        assertThat(view.sessionCount()).isEqualTo(3);
        assertThat(view.tags()).isEqualTo("실내,체조");
        assertThat(view.tuitionType()).isEqualTo(CourseFeeType.PAID);
        assertThat(view.tuitionAmount()).isEqualByComparingTo(new BigDecimal("40000"));
        assertThat(view.institutionName()).isEqualTo(institutionNameOf(course));
    }

    @Test
    void 강사가_없는_강좌는_instructorName_이_null_이다() {
        UUID learnerId = user("학습자2");
        Course course = course("강사없는 교실");
        courseRepository.save(course);
        addSessions(course.id(), 2);
        enroll(learnerId, course.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, null, 50, 0);

        LearningCourseView view = found.stream()
            .filter(v -> v.courseId().equals(course.id())).findFirst().orElseThrow();
        assertThat(view.instructorName()).isNull();
        assertThat(view.sessionCount()).isEqualTo(2);
    }

    @Test
    void 회차가_없는_강좌는_sessionCount_가_0이다() {
        UUID learnerId = user("학습자3");
        Course course = course("회차없는 교실");
        courseRepository.save(course);
        assignMainInstructor(course.id(), "다른 강사");
        enroll(learnerId, course.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, null, 50, 0);

        LearningCourseView view = found.stream()
            .filter(v -> v.courseId().equals(course.id())).findFirst().orElseThrow();
        assertThat(view.sessionCount()).isEqualTo(0);
    }

    private void assignMainInstructor(UUID courseId, String name) {
        UUID userId = user(name);
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        courseInstructorRepository.save(new CourseInstructor(
            UUID.randomUUID(), courseId, userId, CourseInstructorRole.MAIN, now, now));
    }

    private void addSessions(UUID courseId, int count) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        for (int i = 1; i <= count; i++) {
            courseSessionRepository.save(new CourseSession(
                UUID.randomUUID(), courseId, i,
                now.plusSeconds(3600L * i), now.plusSeconds(3600L * i + 1800),
                null, null, null, CourseSessionStatus.SCHEDULED, now, now, null));
        }
    }

    private void enroll(UUID userId, UUID courseId) {
        Instant appliedAt = Instant.parse("2099-08-01T00:00:00Z");
        enrollmentRepository.save(new Enrollment(
            UUID.randomUUID(), courseId, userId, null, EnrollmentApplicationType.SELF,
            EnrollmentStatus.APPROVED, appliedAt, appliedAt, null, null, null, null, null,
            appliedAt, appliedAt));
    }

    private UUID user(String name) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        userRepository.save(new User(id, name, "user-" + id + "@example.com",
            null, LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now));
        return id;
    }

    private String institutionNameOf(Course course) {
        return institutionRepository.findById(course.institutionId())
            .map(Institution::name).orElse(null);
    }

    private Course course(String title) {
        Institution institution = new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            "카드필드 테스트 기관",
            "123-45-67890",
            "홍길동",
            "서울시",
            "02-1234-5678",
            "card@example.com",
            InstitutionStatus.ACTIVE,
            Instant.parse("2099-08-01T00:00:00Z"),
            Instant.parse("2099-08-01T00:00:00Z"),
            null
        );
        institutionRepository.save(institution);

        return new Course(
            UUID.randomUUID(),
            institution.id(),
            uniqueCode("COURSE"),
            title,
            "설명",
            "TSIX_040102",
            CourseEducationType.OFFLINE,
            LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 31),
            LocalDate.of(2099, 8, 1),
            LocalDate.of(2099, 8, 25),
            20,
            "서울 교육장",
            null,
            false,
            null,
            CourseStatus.RECRUITING,
            Instant.parse("2099-08-01T00:00:00Z"),
            Instant.parse("2099-08-02T00:00:00Z"),
            null
        );
    }
}
