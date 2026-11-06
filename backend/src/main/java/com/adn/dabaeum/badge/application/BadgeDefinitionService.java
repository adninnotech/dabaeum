package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배지 정의 관리 (설계서 §11). 이미 발급된 배지가 있는 정의는 규칙을 바꾸거나 지울 수 없다(409) — 준 배지의 의미가 바뀌지 않게.
 * 비활성화는 언제든 된다. 이미 준 배지는 남는다.
 */
@Service
@Profile({"local", "dev"})
public class BadgeDefinitionService {

    private final BadgeDefinitionRepository repository;
    private final CourseRepository courses;
    private final CourseClassificationService classifications;
    private final StoredFileRepository files;
    private final Clock clock;

    public BadgeDefinitionService(
        BadgeDefinitionRepository repository,
        CourseRepository courses,
        CourseClassificationService classifications,
        StoredFileRepository files,
        Clock clock
    ) {
        this.files = Objects.requireNonNull(files);
        this.repository = Objects.requireNonNull(repository);
        this.courses = Objects.requireNonNull(courses);
        this.classifications = Objects.requireNonNull(classifications);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<BadgeDefinition> list(boolean activeOnly) {
        return repository.findAll(activeOnly);
    }

    @Transactional(readOnly = true)
    public BadgeDefinition get(UUID id) {
        return repository.findById(id).orElseThrow(BadgeDefinitionService::notFound);
    }

    @Transactional
    public BadgeDefinition create(BadgeDefinitionCommand command) {
        BadgeRule rule = rule(command);
        Instant now = clock.instant();
        BadgeDefinition definition = build(() -> new BadgeDefinition(
            UUID.randomUUID(), trim(command.code()), trim(command.name()), blankToNull(command.description()),
            command.imageFileId(), rule, true, now, now));
        try {
            repository.save(definition);
        } catch (DataIntegrityViolationException exception) {
            throw integrity(exception);
        }
        return definition;
    }

    @Transactional
    public BadgeDefinition update(UUID id, BadgeDefinitionCommand command) {
        BadgeDefinition existing = get(id);
        BadgeRule rule = rule(command);
        if (!rule.equals(existing.rule()) && !onlyTierLabelsChanged(existing.rule(), rule)
            && repository.hasIssuedBadges(id)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BADGE_DEFINITION_IN_USE,
                "Rule cannot change after badges were issued");
        }
        BadgeDefinition updated = build(() -> existing.withContent(
            trim(command.name()), blankToNull(command.description()), command.imageFileId(), rule, clock.instant()));
        try {
            repository.update(updated);
        } catch (DataIntegrityViolationException exception) {
            throw integrity(exception);
        }
        return updated;
    }

    @Transactional
    public BadgeDefinition setActive(UUID id, boolean active) {
        BadgeDefinition updated = get(id).withActive(active, clock.instant());
        repository.update(updated);
        return updated;
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        if (repository.hasIssuedBadges(id)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BADGE_DEFINITION_IN_USE,
                "Definition with issued badges cannot be deleted");
        }
        repository.delete(id);
    }

    private static boolean onlyTierLabelsChanged(BadgeRule before, BadgeRule after) {
        return before instanceof BadgeRule.Tiered previous && after instanceof BadgeRule.Tiered next
            && previous.sameThresholds(next);
    }

    private BadgeRule rule(BadgeDefinitionCommand command) {
        if (command == null || command.ruleType() == null) {
            throw validation("rule");
        }
        BadgeRule rule = build(() -> switch (command.ruleType()) {
            case COURSE_COMPLETION -> new BadgeRule.CourseCompletion(command.courseId(), command.classificationCode());
            case CUMULATIVE -> new BadgeRule.Cumulative(command.count() == null ? 0 : command.count(),
                command.classificationCode());
            case TIERED -> new BadgeRule.Tiered(command.levels(), command.classificationCode());
        }, "rule");
        if (rule instanceof BadgeRule.Tiered tiered && tiered.levels().stream()
            .anyMatch(level -> level.imageFileId() != null && files.findById(level.imageFileId()).isEmpty())) {
            throw validation("rule.levels.imageFileId");
        }
        if (rule.classificationCode() != null && !classifications.exists(rule.classificationCode())) {
            throw validation("rule.classificationCode");
        }
        if (rule instanceof BadgeRule.CourseCompletion completion && completion.courseId() != null
            && courses.findActiveById(completion.courseId()).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND, "Course not found");
        }
        return rule;
    }

    private static <T> T build(java.util.function.Supplier<T> factory) {
        return build(factory, "requestBody");
    }

    private static <T> T build(java.util.function.Supplier<T> factory, String field) {
        try {
            return factory.get();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw validation(field);
        }
    }

    private static ApiException integrity(DataIntegrityViolationException exception) {
        String message = String.valueOf(exception.getMostSpecificCause().getMessage());
        if (message.contains("uq_tb_badge_definitions_code")) {
            return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BADGE_DEFINITION_CONFLICT,
                "Badge definition code already exists");
        }
        return validation("imageFileId");
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.BADGE_DEFINITION_NOT_FOUND,
            "Badge definition not found");
    }

    private static ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
