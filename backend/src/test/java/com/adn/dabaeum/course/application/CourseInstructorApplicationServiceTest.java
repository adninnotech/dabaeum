package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseInstructorApplicationServiceTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID INSTRUCTOR_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID ASSIGNMENT_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );
    private static final UUID MANAGER_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Mock CourseRepository courses;
    @Mock CourseInstructorRepository instructors;
    @Mock UserRepository users;
    @Mock UserRoleRepository roles;
    @Mock CourseInstructorIdGenerator ids;

    private CourseInstructorApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCourseInstructorApplicationService(
            courses,
            instructors,
            users,
            roles,
            ids,
            new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void assignsAndListsActiveSameInstitutionInstructor() {
        when(courses.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(users.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(user()));
        when(roles.findByUserIdAndInstitutionForUpdate(
            INSTRUCTOR_ID,
            INSTITUTION_ID
        ))
            .thenReturn(List.of(instructorRole()));
        when(ids.generate()).thenReturn(ASSIGNMENT_ID);
        when(instructors.findByCourseId(COURSE_ID)).thenReturn(List.of(
            assignment(CourseInstructorRole.MAIN)
        ));

        CourseInstructorView assigned = service.assign(
            new AssignCourseInstructorCommand(
                COURSE_ID, INSTRUCTOR_ID, CourseInstructorRole.MAIN
            ),
            manager()
        );

        assertThat(assigned.id()).isEqualTo(ASSIGNMENT_ID);
        assertThat(assigned.instructorName()).isEqualTo("강사");
        verify(instructors).save(assignment(CourseInstructorRole.MAIN));
        InOrder lockOrder = org.mockito.Mockito.inOrder(roles, instructors);
        lockOrder.verify(roles).findByUserIdAndInstitutionForUpdate(
            INSTRUCTOR_ID,
            INSTITUTION_ID
        );
        lockOrder.verify(instructors).save(any());
        assertThat(service.list(COURSE_ID)).hasSize(1);
    }

    @Test
    void rejectsUserWithoutScopedInstructorRole() {
        when(courses.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(users.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(user()));
        when(roles.findByUserIdAndInstitutionForUpdate(
            INSTRUCTOR_ID,
            INSTITUTION_ID
        ))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.assign(
            new AssignCourseInstructorCommand(
                COURSE_ID, INSTRUCTOR_ID, CourseInstructorRole.ASSISTANT
            ),
            manager()
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status().value()).isEqualTo(422);
            assertThat(exception.code()).isEqualTo(
                com.adn.dabaeum.common.api.ApiErrorCode.VALIDATION_FAILED
            );
            assertThat(exception.details()).containsExactly("userId");
        });
    }

    @Test
    void rejectsInactiveUserWithUserIdValidationError() {
        when(courses.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(users.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new User(
            INSTRUCTOR_ID, "강사", "instructor@example.com", null, null,
            UserStatus.SUSPENDED, null, NOW, NOW
        )));

        assertThatThrownBy(() -> service.assign(
            new AssignCourseInstructorCommand(
                COURSE_ID, INSTRUCTOR_ID, CourseInstructorRole.ASSISTANT
            ),
            manager()
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status().value()).isEqualTo(422);
            assertThat(exception.code()).isEqualTo(
                com.adn.dabaeum.common.api.ApiErrorCode.VALIDATION_FAILED
            );
            assertThat(exception.details()).containsExactly("userId");
        });
    }

    @Test
    void changesRoleAndRemovesAssignment() {
        when(courses.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(users.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(user()));
        when(instructors.findByCourseIdAndUserId(COURSE_ID, INSTRUCTOR_ID))
            .thenReturn(Optional.of(oldAssignment(CourseInstructorRole.ASSISTANT)))
            .thenReturn(Optional.of(assignment(CourseInstructorRole.MAIN)));
        when(instructors.updateRole(any(), any())).thenReturn(true);
        when(instructors.deleteByCourseIdAndUserId(COURSE_ID, INSTRUCTOR_ID))
            .thenReturn(true);

        CourseInstructorView updated = service.updateRole(
            new UpdateCourseInstructorCommand(
                COURSE_ID, INSTRUCTOR_ID, CourseInstructorRole.MAIN
            ),
            manager()
        );
        CourseInstructorView removed = service.remove(
            COURSE_ID,
            INSTRUCTOR_ID,
            manager()
        );

        assertThat(updated.role()).isEqualTo(CourseInstructorRole.MAIN);
        assertThat(updated.assignedAt()).isEqualTo(NOW);
        assertThat(removed.role()).isEqualTo(CourseInstructorRole.MAIN);
    }

    private Course course() {
        return new Course(
            COURSE_ID, INSTITUTION_ID, "C-1", "강좌", null, null,
            CourseEducationType.ONLINE, LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31), null, null, 20, null, null,
            false, null, CourseStatus.DRAFT, NOW, NOW, null
        );
    }

    private User user() {
        return new User(
            INSTRUCTOR_ID, "강사", "instructor@example.com", "010-1111-2222",
            null, UserStatus.ACTIVE, null, NOW, NOW
        );
    }

    private UserRoleAssignment instructorRole() {
        return new UserRoleAssignment(
            UUID.randomUUID(), INSTRUCTOR_ID, INSTITUTION_ID,
            UserRole.INSTRUCTOR, NOW
        );
    }

    private CourseInstructor assignment(CourseInstructorRole role) {
        return new CourseInstructor(
            ASSIGNMENT_ID, COURSE_ID, INSTRUCTOR_ID, role, NOW, NOW
        );
    }

    private CourseInstructor oldAssignment(CourseInstructorRole role) {
        Instant old = NOW.minusSeconds(3600);
        return new CourseInstructor(
            ASSIGNMENT_ID, COURSE_ID, INSTRUCTOR_ID, role, old, old
        );
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(
            MANAGER_ID,
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }
}
