package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import com.adn.dabaeum.enrollment.domain.MyEnrollmentView;
import com.adn.dabaeum.user.domain.Gender;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class EnrollmentQueryMyBatisRepository implements EnrollmentQueryRepository {

    private final EnrollmentQueryMapper mapper;

    public EnrollmentQueryMyBatisRepository(EnrollmentQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MyEnrollmentView> findMyEnrollments(
        UUID userId, EnrollmentStatus status, int limit, int offset, String sort
    ) {
        EnrollmentQuerySort querySort = EnrollmentQuerySort.from(sort);
        return mapper.selectMyEnrollments(
                userId, status == null ? null : status.name(),
                limit, offset, querySort.field, querySort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countMyEnrollments(UUID userId, EnrollmentStatus status) {
        return mapper.countMyEnrollments(
            userId, status == null ? null : status.name());
    }

    @Override
    public List<InstitutionEnrollmentView> findInstitutionEnrollments(
        UUID institutionId, EnrollmentStatus status, int limit, int offset, String sort
    ) {
        EnrollmentQuerySort querySort = EnrollmentQuerySort.from(sort);
        return mapper.selectInstitutionEnrollments(
                institutionId, status == null ? null : status.name(),
                limit, offset, querySort.field, querySort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countInstitutionEnrollments(UUID institutionId, EnrollmentStatus status) {
        return mapper.countInstitutionEnrollments(
            institutionId, status == null ? null : status.name());
    }

    @Override
    public List<InstitutionEnrollmentView> findAllEnrollments(
        EnrollmentStatus status, UUID institutionId, UUID courseId,
        CourseEducationType educationType, String keyword, int limit, int offset, String sort
    ) {
        EnrollmentQuerySort querySort = EnrollmentQuerySort.from(sort);
        return mapper.selectAllEnrollments(
                status == null ? null : status.name(), institutionId, courseId,
                educationType == null ? null : educationType.name(), keyword,
                limit, offset, querySort.field, querySort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countAllEnrollments(
        EnrollmentStatus status, UUID institutionId, UUID courseId,
        CourseEducationType educationType, String keyword
    ) {
        return mapper.countAllEnrollments(
            status == null ? null : status.name(), institutionId, courseId,
            educationType == null ? null : educationType.name(), keyword);
    }

    @Override
    public com.adn.dabaeum.enrollment.domain.LearningSummary learningSummary(UUID userId) {
        LearningSummaryRow row = mapper.selectLearningSummary(userId);
        if (row == null) {
            return new com.adn.dabaeum.enrollment.domain.LearningSummary(0, 0, 0);
        }
        return new com.adn.dabaeum.enrollment.domain.LearningSummary(
            zeroIfNull(row.applying()), zeroIfNull(row.inProgress()),
            zeroIfNull(row.finished()));
    }

    @Override
    public com.adn.dabaeum.enrollment.domain.LearningActivity learningActivity(UUID userId) {
        LearningActivityRow row = mapper.selectLearningActivity(userId);
        if (row == null) {
            return new com.adn.dabaeum.enrollment.domain.LearningActivity(0, 0, 0);
        }
        // 화면은 정수 퍼센트만 쓰므로 여기서 반올림하고 0~100 으로 자른다.
        double average = row.averageProgressPercent() == null
            ? 0 : row.averageProgressPercent().doubleValue();
        long percent = Math.round(average);
        return new com.adn.dabaeum.enrollment.domain.LearningActivity(
            zeroIfNull(row.totalStudyMinutes()),
            (int) Math.min(100, Math.max(0, percent)),
            zeroIfNull(row.inquiryCount()));
    }

    @Override
    public List<com.adn.dabaeum.enrollment.domain.LearningCourseView> findLearningCourses(
        UUID userId, String learningStatus, String keyword, int limit, int offset
    ) {
        return mapper.selectLearningCourses(userId, learningStatus, keyword, limit, offset)
            .stream()
            .map(row -> new com.adn.dabaeum.enrollment.domain.LearningCourseView(
                row.enrollmentId(), row.courseId(), row.title(), row.institutionName(),
                row.learningStatus(), row.educationType(), row.startDate(),
                row.endDate(), row.appliedAt(), row.instructorName(), row.classificationCode(),
                row.sessionCount(), row.tags(), CourseFeeType.valueOf(row.tuitionType()),
                row.tuitionAmount(), row.thumbnailFileId(),
                zeroIfNull(row.attendedCount())))
            .toList();
    }

    @Override
    public long countLearningCourses(UUID userId, String learningStatus, String keyword) {
        return mapper.countLearningCourses(userId, learningStatus, keyword);
    }

    @Override
    public List<com.adn.dabaeum.enrollment.domain.EnrollmentProgressView>
        findInstructorEnrollmentProgress(
            UUID instructorUserId, UUID courseId, int limit, int offset
    ) {
        return mapper.selectInstructorEnrollmentProgress(
                instructorUserId, courseId, limit, offset)
            .stream().map(this::toProgress).toList();
    }

    @Override
    public long countInstructorEnrollmentProgress(UUID instructorUserId, UUID courseId) {
        return mapper.countInstructorEnrollmentProgress(instructorUserId, courseId);
    }

    @Override
    public java.util.Optional<com.adn.dabaeum.enrollment.domain.EnrollmentProgressView>
        findEnrollmentProgress(UUID enrollmentId) {
        return java.util.Optional.ofNullable(
            mapper.selectEnrollmentProgress(enrollmentId)).map(this::toProgress);
    }

    private com.adn.dabaeum.enrollment.domain.EnrollmentProgressView toProgress(
        EnrollmentProgressViewRow row
    ) {
        com.adn.dabaeum.enrollment.domain.EnrolleeProfile profile =
            new com.adn.dabaeum.enrollment.domain.EnrolleeProfile(
                row.enrolleeName(), row.enrolleeBirthDate(),
                row.enrolleeGender() == null ? null : Gender.valueOf(row.enrolleeGender()),
                row.enrolleeEmail(), row.enrolleePhone(), row.enrolleeProfileImageId());
        return new com.adn.dabaeum.enrollment.domain.EnrollmentProgressView(
            row.enrollmentId(), row.userId(), profile, row.courseId(),
            row.courseTitle(), zeroIfNull(row.attendedCount()),
            zeroIfNull(row.sessionCount()), row.lastStudiedAt(), row.status(), null);
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0 : value;
    }

    private MyEnrollmentView toDomain(MyEnrollmentViewRow row) {
        return new MyEnrollmentView(
            row.id(), row.courseId(), row.userId(),
            EnrollmentStatus.valueOf(row.status()),
            row.appliedAt(), row.createdAt(), row.courseTitle(), row.courseCode(),
            CourseStatus.valueOf(row.courseStatus()), row.institutionName(),
            row.instructorName(), row.classificationCode(), row.educationType(),
            row.sessionCount(), row.tags(), CourseFeeType.valueOf(row.tuitionType()),
            row.tuitionAmount(), row.thumbnailFileId(), row.cancellationReason());
    }

    private InstitutionEnrollmentView toDomain(InstitutionEnrollmentViewRow row) {
        int progressPercent = com.adn.dabaeum.enrollment.domain.EnrollmentProgressView.progressPercent(
            zeroIfNull(row.attendedCount()), zeroIfNull(row.sessionCount()));
        return new InstitutionEnrollmentView(
            row.id(), row.courseId(), row.userId(),
            EnrollmentStatus.valueOf(row.status()),
            row.appliedAt(), row.createdAt(), row.courseTitle(), row.userName(),
            row.institutionName(), progressPercent, row.lastStudiedAt(),
            row.processedAt(), row.waitlistPosition(), row.cancellationReason());
    }

    private enum EnrollmentQuerySort {
        APPLIED_AT_ASC("appliedAt,asc", "applied_at", "ASC"),
        APPLIED_AT_DESC("appliedAt,desc", "applied_at", "DESC"),
        CREATED_AT_ASC("createdAt,asc", "created_at", "ASC"),
        CREATED_AT_DESC("createdAt,desc", "created_at", "DESC");

        private final String value;
        private final String field;
        private final String direction;

        EnrollmentQuerySort(String value, String field, String direction) {
            this.value = value;
            this.field = field;
            this.direction = direction;
        }

        private static EnrollmentQuerySort from(String value) {
            for (EnrollmentQuerySort sort : values()) {
                if (sort.value.equals(value)) {
                    return sort;
                }
            }
            throw new IllegalArgumentException(
                "Unsupported enrollment query sort: " + value);
        }
    }
}
