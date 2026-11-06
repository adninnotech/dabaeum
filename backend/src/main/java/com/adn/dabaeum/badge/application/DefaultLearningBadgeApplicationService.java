package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeEventRepository;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultLearningBadgeApplicationService
    implements LearningBadgeApplicationService {

    private final LearningBadgeRepository badgeRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialGroupRepository groupRepository;
    private final CompletionRepository completionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final AuthorizationPolicy authorizationPolicy;
    private final BadgeDefinitionRepository definitionRepository;
    private final LearningBadgeEventRepository eventRepository;

    public DefaultLearningBadgeApplicationService(
        LearningBadgeRepository badgeRepository,
        CredentialRepository credentialRepository,
        CredentialGroupRepository groupRepository,
        CompletionRepository completionRepository,
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        AuthorizationPolicy authorizationPolicy,
        BadgeDefinitionRepository definitionRepository,
        LearningBadgeEventRepository eventRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.eventRepository = eventRepository;
        this.badgeRepository = badgeRepository;
        this.credentialRepository = credentialRepository;
        this.groupRepository = groupRepository;
        this.completionRepository = completionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional
    public LearningBadge issue(IssueLearningBadgeCommand command) {
        Objects.requireNonNull(command, "command");
        Credential credential = credentialRepository.findById(command.credentialId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND,
                "Credential not found"));
        BadgeScope scope = scope(credential);
        authorizationPolicy.requireCourseManager(command.actor(), scope.institutionId());

        if (credential.status() != CredentialStatus.ISSUED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Credential is not in ISSUED status");
        }

        BadgeDefinition definition = definitionRepository.findById(command.definitionId())
            .orElseThrow(() -> notFound(ApiErrorCode.BADGE_DEFINITION_NOT_FOUND, "Badge definition not found"));
        // 단계 배지는 이수 건수로만 정해진다(설계서 §11.4). 수동으로 주지 않는다.
        if (!definition.active() || definition.rule() instanceof BadgeRule.Tiered) {
            throw validation("definitionId");
        }
        // 정의 배지는 사람당 정의당 1회다. 이미 받았으면(자동 발급 포함) 그 배지를 돌려준다.
        Optional<LearningBadge> existing = badgeRepository.findByUserIdAndDefinitionId(
            scope.userId(), definition.id());
        if (existing.isPresent()) {
            return existing.get();
        }

        UUID courseId = command.courseId() != null
            ? command.courseId()
            : scope.courseId();
        if (command.courseId() != null) {
            courseRepository.findActiveById(command.courseId())
                .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND,
                    "Course not found"));
        }

        LearningBadge badge = new LearningBadge(
            UUID.randomUUID(),
            scope.userId(),
            courseId,
            credential.id(),
            definition.rule().type().name(),
            definition.name(),
            LearningBadgeStatus.ISSUED,
            null,
            command.requestedAt(),
            null,
            command.requestedAt(),
            command.requestedAt(),
            definition.id(),
            definition.code(),
            definition.imageFileId()
        );
        try {
            badgeRepository.save(badge);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.BADGE_CONFLICT,
                "Badge was issued concurrently");
        }
        eventRepository.save(LearningBadgeEvent.of(badge.id(), LearningBadgeEvent.Type.ISSUED, null, null,
            "수료증 기반 수동 발급", command.actor().userId(), command.requestedAt()));
        return badge;
    }

    @Override
    @Transactional(readOnly = true)
    public LearningBadge get(UUID badgeId, AuthenticatedUserContext actor) {
        if (badgeId == null) {
            throw validation("badgeId");
        }
        LearningBadge badge = badgeRepository.findById(badgeId)
            .orElseThrow(() -> notFound(ApiErrorCode.BADGE_NOT_FOUND, "Badge not found"));
        if (Objects.equals(actor.userId(), badge.userId())) {
            return badge;
        }
        if (badge.courseId() != null) {
            Course course = courseRepository.findActiveById(badge.courseId())
                .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND,
                    "Course not found"));
            authorizationPolicy.requireCredentialSubjectOrInstitutionReader(
                actor, badge.userId(), course.institutionId());
            return badge;
        }
        authorizationPolicy.requireCredentialListReader(
            actor, enrollmentRepository.findInstitutionIdsByUserId(badge.userId()));
        return badge;
    }

    @Override
    @Transactional(readOnly = true)
    public LearningBadgePage listByUser(ListUserBadgesQuery query) {
        Objects.requireNonNull(query, "query");
        if (!Objects.equals(query.actor().userId(), query.userId())) {
            authorizationPolicy.requireCredentialListReader(
                query.actor(),
                enrollmentRepository.findInstitutionIdsByUserId(query.userId()));
        }
        int offset = Math.multiplyExact(query.page(), query.size());
        List<LearningBadge> badges = badgeRepository.findByUserId(
            query.userId(), query.size(), offset, query.sort());
        long totalElements = badgeRepository.countByUserId(query.userId());
        int totalPages = totalElements == 0
            ? 0 : (int) ((totalElements + query.size() - 1) / query.size());
        return new LearningBadgePage(
            badges, query.page(), query.size(), totalElements, totalPages);
    }

    private BadgeScope scope(Credential credential) {
        CredentialGroup group = groupRepository.findById(credential.credentialGroupId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND,
                "Credential not found"));
        Completion completion = completionRepository.findById(group.completionId())
            .orElseThrow(() -> notFound(ApiErrorCode.COMPLETION_NOT_FOUND,
                "Completion not found"));
        Enrollment enrollment = enrollmentRepository.findById(completion.enrollmentId())
            .orElseThrow(() -> notFound(ApiErrorCode.ENROLLMENT_NOT_FOUND,
                "Enrollment not found"));
        Course course = courseRepository.findActiveById(enrollment.courseId())
            .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND, "Course not found"));
        return new BadgeScope(enrollment.userId(), course.institutionId(), course.id());
    }

    private ApiException notFound(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field));
    }

    private record BadgeScope(UUID userId, UUID institutionId, UUID courseId) {
    }
}
