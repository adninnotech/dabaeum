package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import com.adn.dabaeum.enrollment.domain.MyEnrollmentView;
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
    public List<com.adn.dabaeum.enrollment.domain.LearningCourseView> findLearningCourses(
        UUID userId, String learningStatus, int limit, int offset
    ) {
        return mapper.selectLearningCourses(userId, learningStatus, limit, offset)
            .stream()
            .map(row -> new com.adn.dabaeum.enrollment.domain.LearningCourseView(
                row.enrollmentId(), row.courseId(), row.title(), row.institutionName(),
                row.learningStatus(), row.educationType(), row.startDate(),
                row.endDate(), row.appliedAt()))
            .toList();
    }

    @Override
    public long countLearningCourses(UUID userId, String learningStatus) {
        return mapper.countLearningCourses(userId, learningStatus);
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
        return new com.adn.dabaeum.enrollment.domain.EnrollmentProgressView(
            row.enrollmentId(), row.userId(), row.userName(), row.courseId(),
            row.courseTitle(), zeroIfNull(row.attendedCount()),
            zeroIfNull(row.sessionCount()), row.status());
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0 : value;
    }

    private MyEnrollmentView toDomain(MyEnrollmentViewRow row) {
        return new MyEnrollmentView(
            row.id(), row.courseId(), row.userId(),
            EnrollmentStatus.valueOf(row.status()),
            row.appliedAt(), row.createdAt(), row.courseTitle(), row.courseCode(),
            CourseStatus.valueOf(row.courseStatus()), row.institutionName());
    }

    private InstitutionEnrollmentView toDomain(InstitutionEnrollmentViewRow row) {
        return new InstitutionEnrollmentView(
            row.id(), row.courseId(), row.userId(),
            EnrollmentStatus.valueOf(row.status()),
            row.appliedAt(), row.createdAt(), row.courseTitle(), row.userName());
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
