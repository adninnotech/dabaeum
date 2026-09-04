package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
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

class CourseInstructorMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseInstructorRepository instructorRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void savesReadsUpdatesAndDeletesInstructorAssignment() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Institution institution = institution();
        institutionRepository.save(institution);
        userRepository.save(user(userId));
        courseRepository.save(course(courseId, institution.id()));

        Instant assignedAt = Instant.parse("2026-08-04T00:00:00Z");
        CourseInstructor main = new CourseInstructor(
            UUID.randomUUID(), courseId, userId, CourseInstructorRole.MAIN,
            assignedAt, assignedAt
        );
        instructorRepository.save(main);

        assertThat(instructorRepository.findByCourseIdAndUserId(courseId, userId))
            .contains(main);
        assertThat(instructorRepository.findByCourseId(courseId))
            .containsExactly(main);
        assertThat(instructorRepository.existsByCourseIdAndUserId(courseId, userId))
            .isTrue();
        assertThat(instructorRepository.existsByCourseIdAndUserIdAndRole(
            courseId, userId, CourseInstructorRole.MAIN)).isTrue();
        assertThat(instructorRepository.existsByCourseIdAndUserIdAndRole(
            courseId, userId, CourseInstructorRole.ASSISTANT)).isFalse();
        assertThat(instructorRepository.existsByUserIdAndInstitutionId(
            userId, institution.id()
        )).isTrue();

        CourseInstructor assistant = new CourseInstructor(
            main.id(), main.courseId(), main.userId(),
            CourseInstructorRole.ASSISTANT, main.assignedAt(), main.createdAt()
        );
        assertThat(instructorRepository.updateRole(
            assistant,
            CourseInstructorRole.MAIN
        )).isTrue();
        assertThat(instructorRepository.findByCourseIdAndUserId(courseId, userId))
            .contains(assistant);

        assertThat(jdbcTemplate.update(
            "UPDATE tb_courses SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
            courseId
        )).isEqualTo(1);
        assertThat(instructorRepository.existsByUserIdAndInstitutionId(
            userId,
            institution.id()
        )).isTrue();

        assertThat(instructorRepository.deleteByCourseIdAndUserId(courseId, userId))
            .isTrue();
        assertThat(instructorRepository.findByCourseId(courseId)).isEmpty();
    }

    private Institution institution() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Institution(
            UUID.randomUUID(), uniqueCode("INST"), "강사 테스트 기관", null, null,
            null, null, null, InstitutionStatus.ACTIVE, now, now, null
        );
    }

    private User user(UUID id) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new User(id, "강사", "instructor-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private Course course(UUID id, UUID institutionId) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Course(id, institutionId, uniqueCode("COURSE"), "강사 과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1), null, null, 10, null, null, false, null,
            CourseStatus.DRAFT, now, now, null);
    }
}
