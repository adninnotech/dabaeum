package com.adn.dabaeum.course.application;

import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.classification.domain.ClassificationPrefix;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CoursePublishRequirements;
import com.adn.dabaeum.course.domain.CourseSort;
import com.adn.dabaeum.course.domain.CourseSortDirection;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseStatusPolicy;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.net.URI;
import com.adn.dabaeum.correction.application.CorrectionReasons;
import com.adn.dabaeum.correction.domain.AdminCorrection;
import com.adn.dabaeum.correction.domain.AdminCorrectionRepository;
import com.adn.dabaeum.correction.domain.CorrectionTargetType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;

@Service
public class DefaultCourseApplicationService
    implements CourseApplicationService {

    private final CourseRepository repository;
    private final InstitutionRepository institutionRepository;
    private final CourseIdGenerator idGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final AdminCorrectionRepository corrections;
    private final Clock clock;
    private final StoredFileRepository storedFileRepository;
    private final CourseCodeGenerator courseCodeGenerator;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseInstructorIdGenerator courseInstructorIdGenerator;
    private final CourseClassificationService classifications;
    private final List<CourseCancelledListener> cancelledListeners;

    public DefaultCourseApplicationService(
        CourseRepository repository,
        InstitutionRepository institutionRepository,
        CourseIdGenerator idGenerator,
        AuthorizationPolicy authorizationPolicy,
        AdminCorrectionRepository corrections,
        Clock clock,
        StoredFileRepository storedFileRepository,
        CourseCodeGenerator courseCodeGenerator,
        CourseInstructorRepository courseInstructorRepository,
        CourseInstructorIdGenerator courseInstructorIdGenerator,
        CourseClassificationService classifications,
        List<CourseCancelledListener> cancelledListeners
    ) {
        this.cancelledListeners = List.copyOf(Objects.requireNonNull(cancelledListeners, "cancelledListeners"));
        this.classifications = Objects.requireNonNull(classifications, "classifications");
        this.corrections = Objects.requireNonNull(corrections, "corrections");
        this.storedFileRepository =
            Objects.requireNonNull(storedFileRepository, "storedFileRepository");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.institutionRepository = Objects.requireNonNull(
            institutionRepository,
            "institutionRepository"
        );
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.authorizationPolicy = Objects.requireNonNull(
            authorizationPolicy,
            "authorizationPolicy"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.courseCodeGenerator = Objects.requireNonNull(
            courseCodeGenerator,
            "courseCodeGenerator"
        );
        this.courseInstructorRepository = Objects.requireNonNull(
            courseInstructorRepository,
            "courseInstructorRepository"
        );
        this.courseInstructorIdGenerator = Objects.requireNonNull(
            courseInstructorIdGenerator,
            "courseInstructorIdGenerator"
        );
    }

    @Override
    @Transactional
    public Course create(
        CreateCourseCommand command,
        AuthenticatedUserContext context
    ) {
        validateCommand(command);
        authorizationPolicy.requireCourseCreator(
            context,
            command.institutionId()
        );
        institutionRepository.findActiveById(command.institutionId())
            .orElseThrow(this::institutionNotFound);

        String courseCode = command.courseCode() == null || command.courseCode().isBlank()
            ? courseCodeGenerator.generate(command.institutionId())
            : command.courseCode().trim();
        String title = normalizeRequired(command.title(), "title");
        requireAssignableClassification(command.classificationCode());
        if (repository.findActiveByInstitutionAndCode(
            command.institutionId(),
            courseCode
        ).isPresent()) {
            throw courseConflict();
        }

        Instant now = clock.instant();
        Course course;
        try {
            course = new Course(
                Objects.requireNonNull(idGenerator.generate(), "generated id"),
                command.institutionId(),
                courseCode,
                title,
                command.description(),
                command.classificationCode(),
                command.educationType(),
                command.startDate(),
                command.endDate(),
                command.recruitStartDate(),
                command.recruitEndDate(),
                command.capacity(),
                command.location(),
                validateOnlineUrl(command.onlineUrl()),
                command.creditBankEligible(),
                command.creditValue(),
                CourseStatus.DRAFT,
                now,
                now,
                null,
                requireThumbnail(command.thumbnailFileId()),
                command.detail()
            );
            repository.save(course);
        } catch (IllegalArgumentException exception) {
            throw validation("requestBody");
        } catch (DataIntegrityViolationException exception) {
            throw courseConflict();
        }
        assignCreatorAsMainInstructorIfInstructor(course, context);
        return course;
    }

    /**
     * 강사가 만든 강좌만 생성자를 MAIN 강사로 배정한다. 기관 관리자/플랫폼 관리자가 만들면
     * 배정하지 않는다(기존 동작 유지). 강좌 저장과 같은 트랜잭션(create() 의 @Transactional) 안에서
     * 실행되므로 배정 실패 시 강좌 생성도 함께 롤백된다.
     */
    private void assignCreatorAsMainInstructorIfInstructor(
        Course course,
        AuthenticatedUserContext context
    ) {
        boolean isInstructor = context.roles().stream().anyMatch(role ->
            "INSTRUCTOR".equals(role.role())
                && Objects.equals(role.institutionId(), course.institutionId()));
        if (!isInstructor) {
            return;
        }
        Instant now = clock.instant();
        courseInstructorRepository.save(new CourseInstructor(
            Objects.requireNonNull(courseInstructorIdGenerator.generate(), "generated id"),
            course.id(),
            context.userId(),
            CourseInstructorRole.MAIN,
            now,
            now
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseView get(UUID courseId, AuthenticatedUserContext context) {
        if (courseId == null) {
            throw validation("courseId");
        }
        CourseView view = repository.findActiveViewById(courseId)
            .orElseThrow(this::courseNotFound);
        // DRAFT 는 그 강좌를 관리할 수 있는 사람에게만 보인다. 존재 자체를 알려주지 않도록
        // 403 이 아니라 404. list() 와 같은 기준(기관 소속 INSTITUTION_ADMIN·INSTRUCTOR) —
        // 특정 강좌에 주강사로 배정됐는지는 보지 않는다.
        if (view.course().status() == CourseStatus.DRAFT
            && !authorizationPolicy.isInstitutionStaff(
                context, view.course().institutionId())) {
            throw courseNotFound();
        }
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public CoursePage list(ListCoursesQuery query, AuthenticatedUserContext context) {
        if (query == null) {
            throw badRequest("query");
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }

        String[] sortParts = splitSort(query.sort());
        CourseSort sort;
        CourseSortDirection direction;
        try {
            sort = CourseSort.fromApiValue(sortParts[0]);
            direction = CourseSortDirection.fromApiValue(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }

        final int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }

        String classificationPrefix = classificationPrefix(query.classificationCode());
        boolean includeAllDraft = authorizationPolicy.isPlatformAdmin(context);
        List<UUID> draftVisibleInstitutionIds = includeAllDraft
            ? List.of()
            : authorizationPolicy.institutionStaffIds(context);

        List<CourseView> data = repository.findActiveViewPage(new CoursePageCriteria(
            offset,
            query.size(),
            sort,
            direction,
            query.keyword(),
            query.educationType(),
            query.selectionMethod(),
            query.status(),
            includeAllDraft,
            draftVisibleInstitutionIds,
            classificationPrefix
        ));
        long totalElements = repository.countActive(
            query.keyword(), query.educationType(), query.selectionMethod(), query.status(),
            includeAllDraft, draftVisibleInstitutionIds, classificationPrefix);
        int totalPages;
        try {
            totalPages = totalElements == 0
                ? 0
                : Math.toIntExact(((totalElements - 1L) / query.size()) + 1L);
        } catch (ArithmeticException exception) {
            throw badRequest("size");
        }

        return new CoursePage(
            List.copyOf(data),
            query.page(),
            query.size(),
            totalElements,
            totalPages
        );
    }

    @Override
    @Transactional
    public Course update(
        UpdateCourseCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.courseId() == null) {
            throw validation("requestBody");
        }
        if (command.presentFieldCount() == 0) {
            throw validation("requestBody");
        }

        Course existing = repository.findActiveById(command.courseId())
            .orElseThrow(this::courseNotFound);
        requireManager(context, existing);
        if (command.classificationCode() != null && command.classificationCode().present()) {
            requireAssignableClassification(command.classificationCode().value());
        }

        CourseUpdateField<String> courseCode = fieldOrAbsent(command.courseCode());
        CourseUpdateField<String> title = fieldOrAbsent(command.title());
        CourseUpdateField<CourseEducationType> educationType = fieldOrAbsent(
            command.educationType()
        );
        CourseUpdateField<LocalDate> startDate = fieldOrAbsent(command.startDate());
        CourseUpdateField<LocalDate> endDate = fieldOrAbsent(command.endDate());
        CourseUpdateField<Integer> capacity = fieldOrAbsent(command.capacity());
        CourseUpdateField<Boolean> creditBankEligible = fieldOrAbsent(
            command.creditBankEligible()
        );
        CourseUpdateField<CourseStatus> status = fieldOrAbsent(command.status());
        CourseUpdateField<CourseDetail> detail = fieldOrAbsent(command.detail());

        if (educationType.present() && educationType.value() == null) {
            throw validation("educationType");
        }
        if (startDate.present() && startDate.value() == null) {
            throw validation("startDate");
        }
        if (endDate.present() && endDate.value() == null) {
            throw validation("endDate");
        }
        if (capacity.present() && capacity.value() == null) {
            throw validation("capacity");
        }
        if (creditBankEligible.present() && creditBankEligible.value() == null) {
            throw validation("creditBankEligible");
        }
        if (status.present() && status.value() == null) {
            throw validation("status");
        }
        CourseUpdateField<LocalDate> recruitEndDate = fieldOrAbsent(command.recruitEndDate());
        if (startDate.present() || recruitEndDate.present()) {
            // 한쪽만 보내도 저장된 값과 비교한다. 둘 다 안 보낸 수정은 예전 데이터를 문제 삼지 않는다.
            requireRecruitEndNotAfterStart(
                valueOrExisting(recruitEndDate, existing.recruitEndDate()),
                valueOrExisting(startDate, existing.startDate()));
        }

        String normalizedCourseCode = existing.courseCode();
        if (courseCode.present()) {
            normalizedCourseCode = normalizeRequired(courseCode.value(), "courseCode");
            if (!normalizedCourseCode.equals(existing.courseCode())
                && repository.findActiveByInstitutionAndCode(
                    existing.institutionId(),
                    normalizedCourseCode
                ).isPresent()) {
                throw courseConflict();
            }
        }

        String normalizedTitle = existing.title();
        if (title.present()) {
            normalizedTitle = normalizeRequired(title.value(), "title");
        }

        CourseStatus targetStatus = existing.status();
        if (status.present()) {
            try {
                targetStatus = CourseStatusPolicy.updateTarget(
                    existing.status(),
                    status.value()
                );
            } catch (IllegalStateException exception) {
                throw courseStatusConflict();
            }
        }
        if (isFinal(existing.status()) && hasNonStatusField(command)) {
            throw courseStatusConflict();
        }

        Instant now = clock.instant();
        Course updated;
        try {
            updated = new Course(
                existing.id(),
                existing.institutionId(),
                normalizedCourseCode,
                normalizedTitle,
                valueOrExisting(command.description(), existing.description()),
                valueOrExisting(command.classificationCode(), existing.classificationCode()),
                valueOrExisting(educationType, existing.educationType()),
                valueOrExisting(startDate, existing.startDate()),
                valueOrExisting(endDate, existing.endDate()),
                valueOrExisting(command.recruitStartDate(), existing.recruitStartDate()),
                valueOrExisting(command.recruitEndDate(), existing.recruitEndDate()),
                valueOrExisting(capacity, existing.capacity()),
                valueOrExisting(command.location(), existing.location()),
                validateOnlineUrl(valueOrExisting(command.onlineUrl(), existing.onlineUrl())),
                valueOrExisting(creditBankEligible, existing.creditBankEligible()),
                valueOrExisting(command.creditValue(), existing.creditValue()),
                targetStatus,
                existing.createdAt(),
                now,
                existing.deletedAt(),
                command.thumbnailFileId() != null && command.thumbnailFileId().present()
                    ? requireThumbnail(command.thumbnailFileId().value())
                    : existing.thumbnailFileId(),
                valueOrExisting(detail, existing.detail())
            );
            if (!repository.updateActive(updated)) {
                throw courseNotFound();
            }
        } catch (IllegalArgumentException exception) {
            throw validation("requestBody");
        } catch (DataIntegrityViolationException exception) {
            throw courseConflict();
        }
        if (targetStatus == CourseStatus.CANCELLED && existing.status() != CourseStatus.CANCELLED) {
            for (CourseCancelledListener listener : cancelledListeners) {
                listener.onCourseCancelled(updated.id(), updated.institutionId(), now);
            }
        }
        return updated;
    }

    @Override
    @Transactional
    public Course publish(
        UUID courseId,
        AuthenticatedUserContext context
    ) {
        return transition(courseId, context, true);
    }

    @Override
    @Transactional
    public Course close(
        UUID courseId,
        AuthenticatedUserContext context
    ) {
        return transition(courseId, context, false);
    }

    @Override
    @Transactional
    public Course reopen(UUID courseId, String reason, AuthenticatedUserContext context) {
        if (courseId == null) {
            throw validation("courseId");
        }
        String correctionReason = CorrectionReasons.require(reason);
        Course existing = repository.findActiveById(courseId).orElseThrow(this::courseNotFound);
        // 정정은 되돌릴 수 없는 전이를 뒤집는 조작이라 플랫폼 관리자만 허용한다.
        authorizationPolicy.requirePlatformAdmin(context);

        CourseStatus target;
        try {
            target = CourseStatusPolicy.reopenTarget(existing.status());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CORRECTION_NOT_ALLOWED,
                "Course recruitment cannot be reopened in its current state");
        }

        Instant now = clock.instant();
        Course updated = new Course(
            existing.id(), existing.institutionId(), existing.courseCode(), existing.title(),
            existing.description(), existing.classificationCode(), existing.educationType(),
            existing.startDate(), existing.endDate(), existing.recruitStartDate(),
            existing.recruitEndDate(), existing.capacity(), existing.location(),
            existing.onlineUrl(), existing.creditBankEligible(), existing.creditValue(),
            target, existing.createdAt(), now, existing.deletedAt(), existing.thumbnailFileId(),
            existing.detail());
        if (!repository.updateActive(updated)) {
            throw courseNotFound();
        }
        corrections.insert(new AdminCorrection(
            UUID.randomUUID(), CorrectionTargetType.COURSE, courseId,
            existing.status().name(), target.name(), correctionReason, context.userId(), now));
        return updated;
    }

    @Override
    @Transactional
    public void deleteDraft(UUID courseId, AuthenticatedUserContext context) {
        if (courseId == null) {
            throw validation("courseId");
        }
        Course existing = repository.findActiveByIdForUpdate(courseId)
            .orElseThrow(this::courseNotFound);
        requireManager(context, existing);
        if (existing.status() != CourseStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_STATUS_CONFLICT,
                "Only draft courses can be deleted");
        }
        if (!repository.softDeleteDraft(courseId, clock.instant())) {
            throw courseNotFound();
        }
    }

    private Course transition(
        UUID courseId,
        AuthenticatedUserContext context,
        boolean publish
    ) {
        if (courseId == null) {
            throw validation("courseId");
        }
        Course existing = repository.findActiveById(courseId)
            .orElseThrow(this::courseNotFound);
        authorizationPolicy.requireCourseManager(
            context,
            existing.institutionId()
        );

        CourseStatus target;
        try {
            target = publish
                ? CourseStatusPolicy.publishTarget(existing.status())
                : CourseStatusPolicy.closeTarget(existing.status());
        } catch (IllegalStateException exception) {
            throw courseStatusConflict();
        }

        if (publish) {
            List<String> missing = CoursePublishRequirements.missingFields(existing);
            if (!missing.isEmpty()) {
                throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                    "Course cannot be published until required fields are filled", missing);
            }
        }

        Course updated = new Course(
            existing.id(),
            existing.institutionId(),
            existing.courseCode(),
            existing.title(),
            existing.description(),
            existing.classificationCode(),
            existing.educationType(),
            existing.startDate(),
            existing.endDate(),
            existing.recruitStartDate(),
            existing.recruitEndDate(),
            existing.capacity(),
            existing.location(),
            existing.onlineUrl(),
            existing.creditBankEligible(),
            existing.creditValue(),
            target,
            existing.createdAt(),
            clock.instant(),
            existing.deletedAt(),
            existing.thumbnailFileId(),
            existing.detail()
        );
        try {
            if (!repository.updateActive(updated)) {
                throw courseNotFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw courseConflict();
        }
        return updated;
    }

    private void requireManager(AuthenticatedUserContext context, Course course) {
        boolean mainAssigned = context != null
            && context.roles().stream().anyMatch(role ->
                "INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), course.institutionId()))
            && courseInstructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireCourseSessionManager(
            context, course.institutionId(), mainAssigned);
    }

    private String[] splitSort(String sort) {
        if (sort == null) {
            throw badRequest("sort");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2) {
            throw badRequest("sort");
        }
        return parts;
    }

    /** 썸네일은 COURSE_THUMBNAIL 용도로 업로드된 파일만 연결할 수 있다. null은 미지정이다. */
    private UUID requireThumbnail(UUID fileId) {
        if (fileId == null) {
            return null;
        }
        StoredFile file = storedFileRepository.findById(fileId)
            .orElseThrow(() -> validation("thumbnailFileId"));
        if (file.purpose() != StoredFilePurpose.COURSE_THUMBNAIL) {
            throw validation("thumbnailFileId");
        }
        return fileId;
    }

    private boolean hasNonStatusField(UpdateCourseCommand command) {
        return command.courseCode() != null && command.courseCode().present()
            || command.title() != null && command.title().present()
            || command.description() != null && command.description().present()
            || command.classificationCode() != null && command.classificationCode().present()
            || command.educationType() != null && command.educationType().present()
            || command.startDate() != null && command.startDate().present()
            || command.endDate() != null && command.endDate().present()
            || command.recruitStartDate() != null && command.recruitStartDate().present()
            || command.recruitEndDate() != null && command.recruitEndDate().present()
            || command.capacity() != null && command.capacity().present()
            || command.location() != null && command.location().present()
            || command.onlineUrl() != null && command.onlineUrl().present()
            || command.creditBankEligible() != null && command.creditBankEligible().present()
            || command.creditValue() != null && command.creditValue().present()
            || command.thumbnailFileId() != null && command.thumbnailFileId().present()
            || command.detail() != null && command.detail().present();
    }

    private boolean isFinal(CourseStatus status) {
        return status == CourseStatus.COMPLETED || status == CourseStatus.CANCELLED;
    }

    private <T> CourseUpdateField<T> fieldOrAbsent(CourseUpdateField<T> field) {
        return field == null ? CourseUpdateField.absent() : field;
    }

    private <T> T valueOrExisting(CourseUpdateField<T> field, T existing) {
        return field.present() ? field.value() : existing;
    }

    private ApiException badRequest(String field) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid",
            List.of(field)
        );
    }

    private void validateCommand(CreateCourseCommand command) {
        if (command == null) {
            throw validation("requestBody");
        }
        if (command.institutionId() == null) {
            throw validation("institutionId");
        }
        validateOptionalText(command.courseCode(), "courseCode", 50);
        validateRequiredText(command.title(), "title", 60);
        if (command.educationType() == null) {
            throw validation("educationType");
        }
        if (command.startDate() != null
            && command.endDate() != null
            && command.startDate().isAfter(command.endDate())) {
            throw validation("startDate");
        }
        if (command.recruitStartDate() != null
            && command.recruitEndDate() != null
            && command.recruitStartDate().isAfter(command.recruitEndDate())) {
            throw validation("recruitStartDate");
        }
        requireRecruitEndNotAfterStart(command.recruitEndDate(), command.startDate());
        if (command.capacity() != null && command.capacity() < 1) {
            throw validation("capacity");
        }
        if (command.creditBankEligible() == null) {
            throw validation("creditBankEligible");
        }
        if (command.creditValue() != null
            && (command.creditValue().compareTo(new java.math.BigDecimal("0.01")) < 0
                || command.creditValue().compareTo(new java.math.BigDecimal("999.99")) > 0)) {
            throw validation("creditValue");
        }
    }

    /** 모집 마감일은 시작일까지(같은 날 허용). 뒤면 422 [recruitEndDate] (QA VAL-02). */
    private void requireRecruitEndNotAfterStart(LocalDate recruitEndDate, LocalDate startDate) {
        if (recruitEndDate != null && startDate != null && recruitEndDate.isAfter(startDate)) {
            throw validation("recruitEndDate");
        }
    }

    private void validateRequiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw validation(field);
        }
    }

    private void validateOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.trim().length() > maxLength) {
            throw validation(field);
        }
    }

    private String validateOnlineUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            if (!URI.create(value).isAbsolute()) {
                throw validation("onlineUrl");
            }
        } catch (IllegalArgumentException exception) {
            throw validation("onlineUrl");
        }
        return value;
    }

    private String normalizeRequired(String value, String field) {
        if (value == null) {
            throw validation(field);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw validation(field);
        }
        return normalized;
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field)
        );
    }

    private ApiException institutionNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found"
        );
    }

    private ApiException courseNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.COURSE_NOT_FOUND,
            "Course not found"
        );
    }

    private ApiException courseConflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.COURSE_CONFLICT,
            "Course already exists"
        );
    }

    private ApiException courseStatusConflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.COURSE_STATUS_CONFLICT,
            "Course status transition is not allowed"
        );
    }
    /** 값이 있을 때만 검사한다 — 초안은 분류 없이 저장할 수 있다(설계서 §12.3). */
    private void requireAssignableClassification(String code) {
        if (code != null) {
            classifications.requireAssignable(code);
        }
    }

    /** 어느 단계 코드든 하위 포함 접두사로: TSIX_040000 → TSIX_04, TSIX_040200 → TSIX_0402. 형식이 틀리면 400. */
    static String classificationPrefix(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return ClassificationPrefix.of(code);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Invalid classificationCode", List.of("classificationCode"));
        }
    }
}
