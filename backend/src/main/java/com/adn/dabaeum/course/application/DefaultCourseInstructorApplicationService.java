package com.adn.dabaeum.course.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCourseInstructorApplicationService
    implements CourseInstructorApplicationService {

    private final CourseRepository courseRepository;
    private final CourseInstructorRepository repository;
    private final UserRepository userRepository;
    private final UserRoleRepository roleRepository;
    private final CourseInstructorIdGenerator idGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultCourseInstructorApplicationService(
        CourseRepository courseRepository,
        CourseInstructorRepository repository,
        UserRepository userRepository,
        UserRoleRepository roleRepository,
        CourseInstructorIdGenerator idGenerator,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.repository = Objects.requireNonNull(repository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseInstructorView> list(UUID courseId) {
        requireCourse(courseId);
        return repository.findByCourseId(courseId).stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Transactional
    public CourseInstructorView assign(
        AssignCourseInstructorCommand command,
        AuthenticatedUserContext context
    ) {
        validate(command);
        Course course = requireCourse(command.courseId());
        authorizationPolicy.requireCourseManager(context, course.institutionId());
        User user = requireEligibleInstructor(command.userId(), course.institutionId());
        if (repository.existsByCourseIdAndUserId(course.id(), user.id())) {
            throw conflict();
        }
        Instant now = clock.instant();
        CourseInstructor instructor = new CourseInstructor(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            course.id(), user.id(), command.role(), now, now
        );
        try {
            repository.save(instructor);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return toView(instructor, user);
    }

    @Override
    @Transactional
    public CourseInstructorView updateRole(
        UpdateCourseInstructorCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.courseId() == null
            || command.userId() == null || command.role() == null) {
            throw validation("role");
        }
        Course course = requireCourse(command.courseId());
        authorizationPolicy.requireCourseManager(context, course.institutionId());
        CourseInstructor current = find(command.courseId(), command.userId());
        CourseInstructor updated = new CourseInstructor(
            current.id(), current.courseId(), current.userId(), command.role(),
            current.role() == command.role() ? current.assignedAt() : clock.instant(),
            current.createdAt()
        );
        try {
            if (!repository.updateRole(updated, current.role())) {
                throw conflict();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return toView(updated);
    }

    @Override
    @Transactional
    public CourseInstructorView remove(
        UUID courseId,
        UUID userId,
        AuthenticatedUserContext context
    ) {
        Course course = requireCourse(courseId);
        authorizationPolicy.requireCourseManager(context, course.institutionId());
        CourseInstructor current = find(courseId, userId);
        if (!repository.deleteByCourseIdAndUserId(courseId, userId)) {
            throw notFound();
        }
        return toView(current);
    }

    private void validate(AssignCourseInstructorCommand command) {
        if (command == null || command.courseId() == null
            || command.userId() == null || command.role() == null) {
            throw validation("role");
        }
    }

    private Course requireCourse(UUID courseId) {
        if (courseId == null) {
            throw validation("courseId");
        }
        return courseRepository.findActiveById(courseId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.COURSE_NOT_FOUND,
                "Course not found"
            )
        );
    }

    private User requireEligibleInstructor(UUID userId, UUID institutionId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.USER_NOT_FOUND,
                "User not found"
            )
        );
        if (user.status() != UserStatus.ACTIVE) {
            throw validation("userId");
        }
        boolean instructor = roleRepository
            .findByUserIdAndInstitutionForUpdate(userId, institutionId)
            .stream()
            .anyMatch(assignment -> assignment.role() == UserRole.INSTRUCTOR);
        if (!instructor) {
            throw validation("userId");
        }
        return user;
    }

    private CourseInstructor find(UUID courseId, UUID userId) {
        return repository.findByCourseIdAndUserId(courseId, userId)
            .orElseThrow(this::notFound);
    }

    private CourseInstructorView toView(CourseInstructor instructor) {
        User user = userRepository.findById(instructor.userId()).orElseThrow(() ->
            new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.USER_NOT_FOUND,
                "User not found"
            )
        );
        return toView(instructor, user);
    }

    private CourseInstructorView toView(CourseInstructor instructor, User user) {
        return new CourseInstructorView(
            instructor.id(), instructor.courseId(), instructor.userId(),
            user.name(), user.email(), instructor.role(),
            instructor.assignedAt(), instructor.createdAt()
        );
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field)
        );
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.COURSE_INSTRUCTOR_NOT_FOUND,
            "Course instructor not found"
        );
    }

    private ApiException conflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.COURSE_INSTRUCTOR_CONFLICT,
            "Course instructor assignment conflicts"
        );
    }
}
