package com.adn.dabaeum.role.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
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
public class DefaultRoleApplicationService implements RoleApplicationService {

    private final UserRoleRepository roleRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final RoleIdGenerator idGenerator;
    private final Clock clock;

    public DefaultRoleApplicationService(
        UserRoleRepository roleRepository,
        UserRepository userRepository,
        InstitutionRepository institutionRepository,
        CourseInstructorRepository courseInstructorRepository,
        RoleIdGenerator idGenerator,
        Clock clock
    ) {
        this.roleRepository = Objects.requireNonNull(
            roleRepository,
            "roleRepository"
        );
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "userRepository"
        );
        this.institutionRepository = Objects.requireNonNull(
            institutionRepository,
            "institutionRepository"
        );
        this.courseInstructorRepository = Objects.requireNonNull(
            courseInstructorRepository,
            "courseInstructorRepository"
        );
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleAssignment> list(UUID userId) {
        User user = requireManageableUser(userId);
        return List.copyOf(roleRepository.findByUserId(user.id()));
    }

    @Override
    @Transactional
    public UserRoleAssignment assign(AssignRoleCommand command) {
        if (command == null || command.userId() == null) {
            throw validation("userId");
        }
        User user = requireManageableUser(command.userId());
        if (command.role() == null) {
            throw validation("role");
        }
        validateScope(command.role(), command.institutionId());
        if (command.institutionId() != null
            && institutionRepository.findById(command.institutionId()).isEmpty()) {
            throw institutionNotFound();
        }
        if (hasAssignment(
            user.id(),
            command.role(),
            command.institutionId()
        )) {
            throw assignmentConflict();
        }

        Instant now = clock.instant();
        UserRoleAssignment assignment = new UserRoleAssignment(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            user.id(),
            command.institutionId(),
            command.role(),
            now
        );
        try {
            roleRepository.save(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw assignmentConflict();
        }
        return assignment;
    }

    @Override
    @Transactional
    public UserRoleAssignment revoke(UUID userId, UUID roleId) {
        User user = requireManageableUser(userId);
        if (roleId == null) {
            throw validation("roleId");
        }
        UserRoleAssignment assignment = roleRepository.findByIdForUpdate(roleId)
            .filter(candidate -> candidate.userId().equals(user.id()))
            .orElseThrow(this::roleNotFound);
        if (roleRepository.findByUserId(user.id()).size() <= 1) {
            throw roleRequired();
        }
        if (assignment.role() == UserRole.INSTRUCTOR
            && courseInstructorRepository.existsByUserIdAndInstitutionId(
                user.id(),
                assignment.institutionId()
            )) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT,
                "Course instructor assignment must be removed first"
            );
        }
        if (!roleRepository.deleteByIdAndUserId(roleId, user.id())) {
            throw roleNotFound();
        }
        return assignment;
    }

    private User requireManageableUser(UUID userId) {
        if (userId == null) {
            throw validation("userId");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(this::userNotFound);
        if (user.status() == UserStatus.WITHDRAWN) {
            throw userStatusForbidden();
        }
        return user;
    }

    private boolean hasAssignment(
        UUID userId,
        UserRole role,
        UUID institutionId
    ) {
        List<UserRoleAssignment> assignments = institutionId == null
            ? roleRepository.findByUserId(userId)
            : roleRepository.findByUserIdAndInstitution(userId, institutionId);
        return assignments.stream().anyMatch(assignment ->
            assignment.role() == role
                && Objects.equals(assignment.institutionId(), institutionId)
        );
    }

    private void validateScope(UserRole role, UUID institutionId) {
        try {
            new UserRoleAssignment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                institutionId,
                role,
                clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            throw validation("institutionId");
        }
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field)
        );
    }

    private ApiException userNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND,
            "User not found"
        );
    }

    private ApiException institutionNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found"
        );
    }

    private ApiException roleNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.ROLE_NOT_FOUND,
            "Role assignment not found"
        );
    }

    private ApiException assignmentConflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT,
            "Role already assigned"
        );
    }

    private ApiException roleRequired() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ROLE_REQUIRED,
            "At least one role is required"
        );
    }

    private ApiException userStatusForbidden() {
        return new ApiException(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.USER_STATUS_FORBIDDEN,
            "Withdrawn users cannot manage roles"
        );
    }
}
