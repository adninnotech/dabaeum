package com.adn.dabaeum.instructor.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.instructor.domain.InstructorApplication;
import com.adn.dabaeum.instructor.domain.InstructorApplicationPageCriteria;
import com.adn.dabaeum.instructor.domain.InstructorApplicationRepository;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultInstructorApplicationService
    implements InstructorApplicationService {

    private final InstructorApplicationRepository repository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRoleRepository roleRepository;
    private final InstructorApplicationIdGenerator idGenerator;
    private final RoleIdGenerator roleIdGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultInstructorApplicationService(
        InstructorApplicationRepository repository,
        UserRepository userRepository,
        InstitutionRepository institutionRepository,
        UserRoleRepository roleRepository,
        InstructorApplicationIdGenerator idGenerator,
        RoleIdGenerator roleIdGenerator,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.institutionRepository = Objects.requireNonNull(institutionRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.roleIdGenerator = Objects.requireNonNull(roleIdGenerator);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public InstructorApplicationView apply(
        ApplyInstructorCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.institutionId() == null) {
            throw validation("institutionId");
        }
        User user = requireActiveContextUser(context);
        Institution institution = institutionRepository.findActiveById(command.institutionId())
            .orElseThrow(this::institutionNotFound);
        if (institution.status() != InstitutionStatus.ACTIVE) {
            throw institutionNotFound();
        }
        if (hasInstructorRole(user.id(), institution.id())
            || repository.findPending(user.id(), institution.id()).isPresent()) {
            throw applicationConflict();
        }
        String message = normalizeOptional(command.applicationMessage(), "applicationMessage");
        Instant now = clock.instant();
        InstructorApplication application = new InstructorApplication(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            user.id(), institution.id(), InstructorApplicationStatus.PENDING,
            message, null, null, now, null, now, now
        );
        try {
            repository.save(application);
        } catch (DataIntegrityViolationException exception) {
            throw applicationConflict();
        }
        return toView(application, user);
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorApplicationView get(
        UUID applicationId,
        AuthenticatedUserContext context
    ) {
        InstructorApplication application = find(applicationId);
        if (context == null) {
            throw unauthorized();
        }
        if (!context.userId().equals(application.userId())) {
            authorizationPolicy.requireCourseManager(context, application.institutionId());
        }
        return toView(application, requireUser(application.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorApplicationPage listMine(
        ListInstructorApplicationsQuery query,
        AuthenticatedUserContext context
    ) {
        if (context == null) {
            throw unauthorized();
        }
        return page(query, context.userId(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorApplicationPage listInstitution(
        ListInstructorApplicationsQuery query,
        AuthenticatedUserContext context
    ) {
        if (query == null || query.institutionId() == null) {
            throw badRequest("institutionId");
        }
        authorizationPolicy.requireCourseManager(context, query.institutionId());
        institutionRepository.findActiveById(query.institutionId())
            .orElseThrow(this::institutionNotFound);
        return page(query, null, query.institutionId());
    }

    @Override
    @Transactional
    public InstructorApplicationView approve(
        UUID applicationId,
        AuthenticatedUserContext context
    ) {
        InstructorApplication pending = findForUpdate(applicationId);
        authorizationPolicy.requireCourseManager(context, pending.institutionId());
        requirePending(pending);
        User user = requireActiveUser(pending.userId());
        if (!hasInstructorRole(user.id(), pending.institutionId())) {
            UserRoleAssignment assignment = new UserRoleAssignment(
                Objects.requireNonNull(roleIdGenerator.generate(), "generated role id"),
                user.id(), pending.institutionId(), UserRole.INSTRUCTOR, clock.instant()
            );
            boolean inserted = roleRepository.saveIfAbsent(assignment);
            if (!inserted && !hasInstructorRole(user.id(), pending.institutionId())) {
                throw applicationConflict();
            }
        }
        Instant now = clock.instant();
        InstructorApplication approved = new InstructorApplication(
            pending.id(), pending.userId(), pending.institutionId(),
            InstructorApplicationStatus.APPROVED, pending.applicationMessage(), null,
            context.userId(), pending.appliedAt(), now, pending.createdAt(), now
        );
        if (!repository.updateReview(approved, InstructorApplicationStatus.PENDING)) {
            throw statusConflict();
        }
        return toView(approved, user);
    }

    @Override
    @Transactional
    public InstructorApplicationView reject(
        RejectInstructorApplicationCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.applicationId() == null) {
            throw validation("applicationId");
        }
        String reason = normalizeRequired(command.rejectionReason(), "rejectionReason");
        InstructorApplication pending = findForUpdate(command.applicationId());
        authorizationPolicy.requireCourseManager(context, pending.institutionId());
        requirePending(pending);
        Instant now = clock.instant();
        InstructorApplication rejected = new InstructorApplication(
            pending.id(), pending.userId(), pending.institutionId(),
            InstructorApplicationStatus.REJECTED, pending.applicationMessage(), reason,
            context.userId(), pending.appliedAt(), now, pending.createdAt(), now
        );
        if (!repository.updateReview(rejected, InstructorApplicationStatus.PENDING)) {
            throw statusConflict();
        }
        return toView(rejected, requireUser(pending.userId()));
    }

    private InstructorApplicationPage page(
        ListInstructorApplicationsQuery query,
        UUID userId,
        UUID institutionId
    ) {
        if (query == null || query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }
        String[] sort = splitSort(query.sort());
        int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }
        InstructorApplicationPageCriteria criteria = new InstructorApplicationPageCriteria(
            userId, institutionId, query.status(), offset, query.size(), sort[0], sort[1]
        );
        List<InstructorApplicationView> data = repository.findPage(criteria).stream()
            .map(application -> toView(application, requireUser(application.userId())))
            .toList();
        long total = repository.count(criteria);
        int pages = total == 0 ? 0 : Math.toIntExact(((total - 1) / query.size()) + 1);
        return new InstructorApplicationPage(data, query.page(), query.size(), total, pages);
    }

    private String[] splitSort(String value) {
        if (value == null) {
            throw badRequest("sort");
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw badRequest("sort");
        }
        String sort = switch (parts[0]) {
            case "appliedAt" -> "APPLIED_AT";
            case "reviewedAt" -> "REVIEWED_AT";
            case "status" -> "STATUS";
            default -> throw badRequest("sort");
        };
        String direction = parts[1].toUpperCase(Locale.ROOT);
        if (!direction.equals("ASC") && !direction.equals("DESC")) {
            throw badRequest("sort");
        }
        return new String[]{sort, direction};
    }

    private InstructorApplication find(UUID id) {
        if (id == null) {
            throw validation("applicationId");
        }
        return repository.findById(id).orElseThrow(this::notFound);
    }

    private InstructorApplication findForUpdate(UUID id) {
        if (id == null) {
            throw validation("applicationId");
        }
        return repository.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private void requirePending(InstructorApplication application) {
        if (application.status() != InstructorApplicationStatus.PENDING) {
            throw statusConflict();
        }
    }

    private User requireActiveContextUser(AuthenticatedUserContext context) {
        if (context == null) {
            throw unauthorized();
        }
        return requireActiveUser(context.userId());
    }

    private User requireActiveUser(UUID userId) {
        User user = requireUser(userId);
        if (user.status() != UserStatus.ACTIVE) {
            throw validation("userId");
        }
        return user;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "User not found"
        ));
    }

    private boolean hasInstructorRole(UUID userId, UUID institutionId) {
        return roleRepository.findByUserIdAndInstitution(userId, institutionId).stream()
            .anyMatch(role -> role.role() == UserRole.INSTRUCTOR);
    }

    private InstructorApplicationView toView(
        InstructorApplication application,
        User user
    ) {
        return new InstructorApplicationView(
            application.id(), application.userId(), user.name(), user.email(), user.phone(),
            application.institutionId(), application.status(),
            application.applicationMessage(), application.rejectionReason(),
            application.reviewedBy(), application.appliedAt(), application.reviewedAt(),
            application.createdAt(), application.updatedAt()
        );
    }

    private String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 1000) {
            throw validation(field);
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value, field);
        if (normalized == null) {
            throw validation(field);
        }
        return normalized;
    }

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED, "Request validation failed", List.of(field));
    }

    private ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
            "Invalid request", List.of(field));
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
            "Authentication required");
    }

    private ApiException institutionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTRUCTOR_APPLICATION_NOT_FOUND,
            "Instructor application not found");
    }

    private ApiException applicationConflict() {
        return new ApiException(HttpStatus.CONFLICT,
            ApiErrorCode.INSTRUCTOR_APPLICATION_CONFLICT,
            "Instructor application conflict");
    }

    private ApiException statusConflict() {
        return new ApiException(HttpStatus.CONFLICT,
            ApiErrorCode.INSTRUCTOR_APPLICATION_STATUS_CONFLICT,
            "Instructor application status has changed");
    }
}
