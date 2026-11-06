package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseMyBatisRepository implements CourseRepository {

    private final CourseMapper mapper;

    public CourseMyBatisRepository(CourseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Course course) {
        mapper.insert(toRow(course));
    }

    @Override
    public Optional<Course> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Course> findActiveById(UUID id) {
        return Optional.ofNullable(mapper.selectActiveById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Course> findActiveByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Course> findActiveByInstitutionAndCode(
        UUID institutionId,
        String courseCode
    ) {
        return Optional.ofNullable(mapper.selectActiveByInstitutionAndCode(
            institutionId,
            courseCode
        )).map(this::toDomain);
    }

    @Override
    public List<Course> findActivePage(CoursePageCriteria criteria) {
        return mapper.selectActivePage(criteria)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countActive(
        String keyword, CourseEducationType educationType,
        CourseSelectionMethod selectionMethod, CourseStatus status,
        boolean includeAllDraft, List<UUID> draftVisibleInstitutionIds, String classificationPrefix
    ) {
        return mapper.countActiveFiltered(
            keyword, educationType, selectionMethod, status,
            includeAllDraft, draftVisibleInstitutionIds, classificationPrefix);
    }

    @Override
    public boolean updateActive(Course course) {
        return mapper.updateActive(toRow(course)) == 1;
    }

    @Override
    public boolean softDeleteDraft(UUID id, Instant deletedAt) {
        return mapper.softDeleteDraft(id, deletedAt) == 1;
    }

    @Override
    public Optional<CourseView> findActiveViewById(UUID id) {
        return Optional.ofNullable(mapper.selectActiveViewById(id))
            .map(this::toDomain);
    }

    @Override
    public List<CourseView> findActiveViewsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectActiveViewsByIds(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CourseView> findActiveViewPage(CoursePageCriteria criteria) {
        return mapper.selectActiveViewPage(criteria)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private CourseRow toRow(Course course) {
        CourseDetail detail = course.detail();
        return new CourseRow(
            course.id(),
            course.institutionId(),
            course.courseCode(),
            course.title(),
            course.description(),
            course.classificationCode(),
            course.educationType().name(),
            course.startDate(),
            course.endDate(),
            course.recruitStartDate(),
            course.recruitEndDate(),
            course.capacity(),
            course.location(),
            course.onlineUrl(),
            course.creditBankEligible(),
            course.creditValue(),
            course.status().name(),
            course.createdAt(),
            course.updatedAt(),
            course.deletedAt(),
            course.thumbnailFileId(),
            detail.tags(),
            detail.targetAudiencesAsText(),
            detail.instructorProfileVisible(),
            detail.tuitionType().name(),
            detail.tuitionAmount(),
            detail.materialFeeType().name(),
            detail.materialFeeAmount(),
            detail.completionEnabled(),
            detail.completionCriteria() == null ? null : detail.completionCriteria().name(),
            detail.requiredProgressRate(),
            detail.issueCertificate(),
            detail.notes(),
            detail.materials(),
            detail.learningObjective(),
            detail.selectionMethod().name(),
            detail.waitlistCapacity()
        );
    }

    private CourseView toDomain(CourseViewRow row) {
        return new CourseView(
            toDomain(row.toCourseRow()), zeroIfNull(row.enrolledCount()),
            row.institutionName(), row.mainInstructorName());
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0 : value;
    }

    private Course toDomain(CourseRow row) {
        return new Course(
            row.id(),
            row.institutionId(),
            row.courseCode(),
            row.title(),
            row.description(),
            row.classificationCode(),
            CourseEducationType.valueOf(row.educationType()),
            row.startDate(),
            row.endDate(),
            row.recruitStartDate(),
            row.recruitEndDate(),
            row.capacity(),
            row.location(),
            row.onlineUrl(),
            row.creditBankEligible(),
            row.creditValue(),
            CourseStatus.valueOf(row.status()),
            row.createdAt(),
            row.updatedAt(),
            row.deletedAt(),
            row.thumbnailFileId(),
            row.toDetail()
        );
    }
}
