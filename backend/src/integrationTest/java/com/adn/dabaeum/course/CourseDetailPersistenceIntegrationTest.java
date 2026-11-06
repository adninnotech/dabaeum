package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseCompletionCriteria;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseTargetAudience;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.course.domain.InstructorCourseView;
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

class CourseDetailPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseRepository repository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Autowired
    CourseQueryRepository queryRepository;

    @Autowired
    CourseInstructorRepository instructorRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void 기관_강좌_목록_조회는_상세설정_컬럼이_있어도_500이_나지_않는다() {
        CourseDetail detail = CourseDetail.empty().withTags("#기관목록조회");
        Course course = course("COURSE-DETAIL-LIST", "기관목록 과정").withDetail(detail);
        repository.save(course);

        List<CourseView> found = queryRepository.findInstitutionCourses(
            course.institutionId(), null, 50, 0, "createdAt,desc");

        assertThat(found)
            .extracting(view -> view.course().id())
            .contains(course.id());
    }

    @Test
    void 기관_강좌_목록_조회도_상세설정_값을_그대로_돌려준다() {
        CourseDetail detail = CourseDetail.empty().withTags("#기관목록상세");
        Course course = course("COURSE-DETAIL-LIST-VALUE", "기관목록 상세 과정").withDetail(detail);
        repository.save(course);

        List<CourseView> found = queryRepository.findInstitutionCourses(
            course.institutionId(), null, 50, 0, "createdAt,desc");

        assertThat(found)
            .filteredOn(view -> view.course().id().equals(course.id()))
            .hasSize(1)
            .first()
            .satisfies(view -> assertThat(view.course().detail().tags())
                .isEqualTo("#기관목록상세"));
    }

    @Test
    void 강사_강좌_목록_조회도_상세설정_값을_그대로_돌려준다() {
        CourseDetail detail = CourseDetail.empty()
            .withTags("#강사목록상세")
            .withTuition(CourseFeeType.PAID, new BigDecimal("400000"))
            .withCompletion(true, CourseCompletionCriteria.PROGRESS, 80, true);
        Course course = course("COURSE-DETAIL-INSTRUCTOR-LIST", "강사목록 상세 과정")
            .withDetail(detail);
        repository.save(course);

        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        userRepository.save(new User(userId, "강사", "instructor-" + userId + "@example.com",
            null, LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now));
        instructorRepository.save(new CourseInstructor(
            UUID.randomUUID(), course.id(), userId, CourseInstructorRole.MAIN, now, now));

        List<InstructorCourseView> found = queryRepository.findInstructorCourses(
            userId, null, 50, 0, "createdAt,desc");

        assertThat(found)
            .filteredOn(view -> view.course().id().equals(course.id()))
            .hasSize(1)
            .first()
            .satisfies(view -> {
                assertThat(view.course().detail().tags()).isEqualTo("#강사목록상세");
                assertThat(view.course().detail().tuitionType()).isEqualTo(CourseFeeType.PAID);
                assertThat(view.course().detail().tuitionAmount())
                    .isEqualByComparingTo(new BigDecimal("400000"));
                assertThat(view.course().detail().completionEnabled()).isTrue();
            });
    }

    @Test
    void 저장한_상세설정이_그대로_다시_읽힌다() {
        CourseDetail detail = CourseDetail.empty()
            .withTags("#보드게임 #취미 #소통")
            .withTargetAudiences(List.of(
                CourseTargetAudience.ADULTS, CourseTargetAudience.MIDDLE_AGED_SENIORS))
            .withTuition(CourseFeeType.PAID, new BigDecimal("40000"))
            .withMaterialFee(CourseFeeType.PAID, new BigDecimal("10000"))
            .withCompletion(true, CourseCompletionCriteria.PROGRESS, 80, true);
        Course course = course("COURSE-DETAIL", "상세설정 과정").withDetail(detail);
        repository.save(course);

        Course found = repository.findActiveById(course.id()).orElseThrow();

        assertThat(found.detail().tags()).isEqualTo("#보드게임 #취미 #소통");
        assertThat(found.detail().targetAudiences())
            .containsExactlyInAnyOrder(
                CourseTargetAudience.ADULTS, CourseTargetAudience.MIDDLE_AGED_SENIORS);
        assertThat(found.detail().tuitionType()).isEqualTo(CourseFeeType.PAID);
        assertThat(found.detail().tuitionAmount())
            .isEqualByComparingTo(new BigDecimal("40000"));
        assertThat(found.detail().materialFeeAmount())
            .isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(found.detail().completionEnabled()).isTrue();
        assertThat(found.detail().completionCriteria())
            .isEqualTo(CourseCompletionCriteria.PROGRESS);
        assertThat(found.detail().requiredProgressRate()).isEqualTo(80);
        assertThat(found.detail().issueCertificate()).isTrue();
    }

    @Test
    void 상세설정_없이_저장하면_기본값으로_읽힌다() {
        Course course = course("COURSE-DETAIL-DEFAULT", "기본값 과정");
        repository.save(course);

        Course found = repository.findActiveById(course.id()).orElseThrow();

        assertThat(found.detail().tuitionType()).isEqualTo(CourseFeeType.FREE);
        assertThat(found.detail().targetAudiences()).isEmpty();
        assertThat(found.detail().tags()).isNull();
        assertThat(found.detail().instructorProfileVisible()).isTrue();
    }

    @Test
    void 상세설정만_수정해도_반영된다() {
        Course course = course("COURSE-DETAIL-UPDATE", "수정 과정");
        repository.save(course);

        repository.updateActive(course.withDetail(
            CourseDetail.empty().withTags("#수정됨")));

        Course found = repository.findActiveById(course.id()).orElseThrow();
        assertThat(found.detail().tags()).isEqualTo("#수정됨");
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
}
