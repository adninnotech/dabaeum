package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 강사 통계의 studentCount·inquiryCount 가 같은 화면의 수강 현황 탭·문의 탭 totalElements 와
 * 같은 숫자를 내는지 증명한다. 두 진입점이 다른 숫자를 보이면 어느 쪽이 맞는지 아무도 판정
 * 못 한다. 배정되지 않은 강좌의 신청·문의가 섞이지 않는지도 함께 본다.
 */
class InstructorCourseStatsMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2099-08-01T00:00:00Z");

    @Autowired CourseRepository courseRepository;
    @Autowired CourseQueryRepository courseQueryRepository;
    @Autowired CourseInstructorRepository courseInstructorRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired EnrollmentQueryRepository enrollmentQueryRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 수강생_수와_문의_수가_배정_강좌에서만_세지고_탭_건수와_같다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course recruiting = course(institution, CourseStatus.RECRUITING);
        Course inProgress = course(institution, CourseStatus.IN_PROGRESS);
        Course unassigned = course(institution, CourseStatus.RECRUITING);
        courseRepository.save(recruiting);
        courseRepository.save(inProgress);
        courseRepository.save(unassigned);

        User instructor = user("통계강사");
        userRepository.save(instructor);
        assign(recruiting.id(), instructor.id());
        assign(inProgress.id(), instructor.id());

        enroll(recruiting.id(), EnrollmentStatus.APPROVED);
        enroll(recruiting.id(), EnrollmentStatus.APPROVED);
        enroll(recruiting.id(), EnrollmentStatus.APPLIED);
        enroll(inProgress.id(), EnrollmentStatus.APPROVED);
        enroll(unassigned.id(), EnrollmentStatus.APPROVED);

        inquire(recruiting.id(), InquiryStatus.PENDING, instructor.id());
        inquire(inProgress.id(), InquiryStatus.ANSWERED, instructor.id());
        inquire(unassigned.id(), InquiryStatus.PENDING, instructor.id());

        InstructorCourseStats stats = courseQueryRepository.instructorCourseStats(instructor.id());

        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.recruiting()).isEqualTo(1);
        assertThat(stats.inProgress()).isEqualTo(1);
        assertThat(stats.studentCount()).isEqualTo(3);
        assertThat(stats.inquiryCount()).isEqualTo(2);
        assertThat(stats.studentCount()).isEqualTo(
            enrollmentQueryRepository.countInstructorEnrollmentProgress(instructor.id(), null));
        assertThat(stats.inquiryCount()).isEqualTo(
            inquiryRepository.countByInstructorUserId(instructor.id(), null, null));
    }

    @Test
    void 배정_강좌가_없으면_모두_0이다() {
        User instructor = user("미배정강사");
        userRepository.save(instructor);

        InstructorCourseStats stats = courseQueryRepository.instructorCourseStats(instructor.id());

        assertThat(stats).isEqualTo(new InstructorCourseStats(0, 0, 0, 0, 0, 0));
    }

    private void assign(UUID courseId, UUID userId) {
        courseInstructorRepository.save(new CourseInstructor(
            UUID.randomUUID(), courseId, userId, CourseInstructorRole.MAIN, NOW, NOW));
    }

    private void enroll(UUID courseId, EnrollmentStatus status) {
        User learner = user("수강생");
        userRepository.save(learner);
        Instant approvedAt = status == EnrollmentStatus.APPROVED ? NOW : null;
        enrollmentRepository.save(new Enrollment(
            UUID.randomUUID(), courseId, learner.id(), null,
            EnrollmentApplicationType.SELF, status, NOW, approvedAt, null, null, null,
            null, null, NOW, NOW));
    }

    private void inquire(UUID courseId, InquiryStatus status, UUID answeredBy) {
        User learner = user("문의자");
        userRepository.save(learner);
        boolean answered = status == InquiryStatus.ANSWERED;
        inquiryRepository.save(new Inquiry(
            UUID.randomUUID(), learner.id(), courseId, "문의 제목", "문의 내용", status,
            answered ? "답변" : null, answered ? answeredBy : null, answered ? NOW : null,
            NOW, NOW, InquiryType.COURSE));
    }

    private Institution institution() {
        return new Institution(
            UUID.randomUUID(), uniqueCode("INST"), "강사 통계 테스트 기관", "123-45-67890",
            "홍길동", "서울시", "02-1234-5678", "instructor-stats@example.com",
            InstitutionStatus.ACTIVE, NOW, NOW, null);
    }

    private Course course(Institution institution, CourseStatus status) {
        return new Course(
            UUID.randomUUID(), institution.id(), uniqueCode("COURSE"), "강사 통계 테스트 과정",
            "과정 설명", "TSIX_040201", CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1), LocalDate.of(2099, 10, 31),
            LocalDate.of(2099, 8, 1), LocalDate.of(2099, 8, 25),
            16, "서울 교육장", "https://example.test/course", true, null,
            status, NOW, NOW, null);
    }

    private User user(String name) {
        UUID id = UUID.randomUUID();
        return new User(id, name, "user-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, NOW, NOW);
    }
}
