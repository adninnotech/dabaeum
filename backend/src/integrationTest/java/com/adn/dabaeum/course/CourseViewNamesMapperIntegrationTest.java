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
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.course.domain.InstitutionInstructorView;
import com.adn.dabaeum.course.domain.InstructorCourseView;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
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
 * Course 목록·상세 투영의 institutionName·mainInstructorName 과, 기관 소속 강사 목록의
 * lastLoginAt·birthDate 가 courseViewColumns 조각과 CourseQueryMapper 의 인라인 서브쿼리
 * 양쪽에서 같은 값을 내는지 증명한다. 빈 결과로만 부르면 컬럼이 안 맞아도 안 걸린다.
 */
class CourseViewNamesMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CourseRepository courseRepository;
    @Autowired CourseQueryRepository courseQueryRepository;
    @Autowired CourseInstructorRepository courseInstructorRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;

    @Test
    void 강사가_배정되지_않은_과정은_기관명만_채워지고_주강사명은_null_이다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course course = course(institution);
        courseRepository.save(course);

        CourseView byId = courseRepository.findActiveViewById(course.id()).orElseThrow();
        assertThat(byId.institutionName()).isEqualTo(institution.name());
        assertThat(byId.mainInstructorName()).isNull();
        assertThat(courseRepository.findActiveViewsByIds(java.util.List.of(course.id(), java.util.UUID.randomUUID())))
            .singleElement()
            .satisfies(view -> assertThat(view.institutionName()).isEqualTo(institution.name()));

        CourseView fromInstitutionList = courseQueryRepository
            .findInstitutionCourses(institution.id(), null, 50, 0, "createdAt,desc")
            .stream()
            .filter(view -> view.course().id().equals(course.id()))
            .findFirst()
            .orElseThrow();
        assertThat(fromInstitutionList.institutionName()).isEqualTo(institution.name());
        assertThat(fromInstitutionList.mainInstructorName()).isNull();
    }

    @Test
    void MAIN_강사가_배정되면_주강사명이_읽기_조회에_실린다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course course = course(institution);
        courseRepository.save(course);

        User main = user("주강사");
        User assistant = user("보조강사");
        userRepository.save(main);
        userRepository.save(assistant);

        Instant firstAssignedAt = Instant.parse("2099-08-01T00:00:00Z");
        Instant secondAssignedAt = Instant.parse("2099-08-02T00:00:00Z");
        courseInstructorRepository.save(new CourseInstructor(
            UUID.randomUUID(), course.id(), main.id(), CourseInstructorRole.MAIN,
            firstAssignedAt, firstAssignedAt));
        courseInstructorRepository.save(new CourseInstructor(
            UUID.randomUUID(), course.id(), assistant.id(), CourseInstructorRole.ASSISTANT,
            secondAssignedAt, secondAssignedAt));

        assertThat(courseRepository.findActiveViewById(course.id()).orElseThrow()
            .mainInstructorName()).isEqualTo("주강사");

        CourseView fromInstitutionList = courseQueryRepository
            .findInstitutionCourses(institution.id(), null, 50, 0, "createdAt,desc")
            .stream()
            .filter(view -> view.course().id().equals(course.id()))
            .findFirst()
            .orElseThrow();
        assertThat(fromInstitutionList.institutionName()).isEqualTo(institution.name());
        assertThat(fromInstitutionList.mainInstructorName()).isEqualTo("주강사");

        InstructorCourseView fromInstructorList = courseQueryRepository
            .findInstructorCourses(main.id(), null, 50, 0, "createdAt,desc")
            .stream()
            .filter(view -> view.course().id().equals(course.id()))
            .findFirst()
            .orElseThrow();
        assertThat(fromInstructorList.institutionName()).isEqualTo(institution.name());
        assertThat(fromInstructorList.mainInstructorName()).isEqualTo("주강사");
    }

    @Test
    void 기관_소속_강사_목록에_최근_로그인과_생년월일이_실린다() {
        Institution institution = institution();
        institutionRepository.save(institution);

        Instant lastLoginAt = Instant.parse("2099-09-01T10:00:00Z");
        LocalDate birthDate = LocalDate.of(1990, 5, 20);
        User loggedIn = new User(
            UUID.randomUUID(), "로그인강사", "logged-in-" + UUID.randomUUID() + "@example.com",
            null, birthDate, UserStatus.ACTIVE, null,
            Instant.parse("2099-08-01T00:00:00Z"), Instant.parse("2099-08-01T00:00:00Z"),
            null, null, null, null, lastLoginAt, true, false, null);
        User neverLoggedIn = user("미로그인강사");
        userRepository.save(loggedIn);
        userRepository.save(neverLoggedIn);

        assignInstructorRole(loggedIn.id(), institution.id());
        assignInstructorRole(neverLoggedIn.id(), institution.id());

        List<InstitutionInstructorView> instructors = courseQueryRepository
            .findInstitutionInstructors(institution.id(), null, null, 50, 0, "name,asc");

        InstitutionInstructorView loggedInView = instructors.stream()
            .filter(view -> view.userId().equals(loggedIn.id()))
            .findFirst()
            .orElseThrow();
        assertThat(loggedInView.lastLoginAt()).isEqualTo(lastLoginAt);
        assertThat(loggedInView.birthDate()).isEqualTo(birthDate);

        InstitutionInstructorView neverLoggedInView = instructors.stream()
            .filter(view -> view.userId().equals(neverLoggedIn.id()))
            .findFirst()
            .orElseThrow();
        assertThat(neverLoggedInView.lastLoginAt()).isNull();
    }

    private void assignInstructorRole(UUID userId, UUID institutionId) {
        userRoleRepository.save(new UserRoleAssignment(
            UUID.randomUUID(), userId, institutionId, UserRole.INSTRUCTOR,
            Instant.parse("2099-08-01T00:00:00Z")));
    }

    private Institution institution() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            "이름_투영_테스트_기관_" + uniqueCode(""),
            "123-45-67890",
            "홍길동",
            "서울시",
            "02-1234-5678",
            "course-view-names@example.com",
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
            "이름 투영 테스트 과정",
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

    private User user(String name) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, name, "user-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }
}
