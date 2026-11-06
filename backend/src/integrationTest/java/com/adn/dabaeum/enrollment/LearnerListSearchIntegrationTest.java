package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 학습 목록 4탭 공통 검색(q): CourseCardSql.keywordFilter 가 실제 Postgres 에서
 * 강의명·기관명·강사명을 맞게 걸러내는지, 그리고 KeywordQuery 의 LIKE 이스케이프가
 * 실제로 동작하는지 본다. 네 매퍼가 같은 조각을 include 하므로 하나(학습 중 목록)로
 * 조인 자체의 정확성을 검증한다 — 나머지 세 매퍼는 LearnerListSearchTest(컨트롤러)가
 * q 파라미터 배선을 확인한다. 공유 dev DB 라 이 테스트가 만든 강좌로만 범위를 좁힌다.
 */
class LearnerListSearchIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentQueryRepository enrollmentQueryRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseInstructorRepository courseInstructorRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 강의명으로_찾는다() {
        UUID learnerId = user("검색학습자1");
        Course target = course("수채화로 그리는 일상", "실습 문화원");
        Course other = course("코딩 첫걸음", "실습 문화원");
        courseRepository.save(target);
        courseRepository.save(other);
        enroll(learnerId, target.id());
        enroll(learnerId, other.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, KeywordQuery.normalize("수채화"), 50, 0);

        assertThat(found).extracting(LearningCourseView::courseId)
            .contains(target.id())
            .doesNotContain(other.id());
    }

    @Test
    void 기관명으로도_찾는다() {
        UUID learnerId = user("검색학습자2");
        Course target = course("건강 교실", "서울평생교육원_검색전용");
        Course other = course("요리 교실", "부산평생교육원_검색전용");
        courseRepository.save(target);
        courseRepository.save(other);
        enroll(learnerId, target.id());
        enroll(learnerId, other.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, KeywordQuery.normalize("서울평생교육원_검색전용"), 50, 0);

        assertThat(found).extracting(LearningCourseView::courseId)
            .contains(target.id())
            .doesNotContain(other.id());
    }

    @Test
    void 강사명으로도_찾는다() {
        UUID learnerId = user("검색학습자3");
        Course target = course("건강 교실", "실습 문화원");
        Course other = course("요리 교실", "실습 문화원");
        courseRepository.save(target);
        courseRepository.save(other);
        assignMainInstructor(target.id(), "김검색강사");
        assignMainInstructor(other.id(), "이다른강사");
        enroll(learnerId, target.id());
        enroll(learnerId, other.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, KeywordQuery.normalize("김검색강사"), 50, 0);

        assertThat(found).extracting(LearningCourseView::courseId)
            .contains(target.id())
            .doesNotContain(other.id());
    }

    /** KeywordQuery 의 '%' 이스케이프가 빠지면 리터럴 %가 없는 강좌까지 걸려 나와 실패한다. */
    @Test
    void 퍼센트를_이스케이프해서_리터럴_퍼센트가_든_강좌만_찾는다() {
        UUID learnerId = user("검색학습자4");
        Course withPercent = course("50%할인 특강", "실습 문화원");
        Course withoutPercent = course("일반 특강", "실습 문화원");
        courseRepository.save(withPercent);
        courseRepository.save(withoutPercent);
        enroll(learnerId, withPercent.id());
        enroll(learnerId, withoutPercent.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, KeywordQuery.normalize("%"), 50, 0);

        assertThat(found).extracting(LearningCourseView::courseId)
            .contains(withPercent.id())
            .doesNotContain(withoutPercent.id());
    }

    private void assignMainInstructor(UUID courseId, String name) {
        UUID userId = user(name);
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        courseInstructorRepository.save(new CourseInstructor(
            UUID.randomUUID(), courseId, userId, CourseInstructorRole.MAIN, now, now));
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

    private Course course(String title, String institutionName) {
        Institution institution = new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            institutionName,
            "123-45-67890",
            "홍길동",
            "서울시",
            "02-1234-5678",
            "search@example.com",
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
