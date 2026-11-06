package com.adn.dabaeum.inquiry.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import com.adn.dabaeum.user.domain.Gender;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class InquiryMyBatisRepository implements InquiryRepository {

    private final InquiryMapper mapper;

    public InquiryMyBatisRepository(InquiryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Inquiry inquiry) {
        mapper.insert(toRow(inquiry));
    }

    @Override
    public Optional<Inquiry> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean updateAnswer(Inquiry inquiry) {
        return mapper.updateAnswer(toRow(inquiry)) == 1;
    }

    @Override
    public List<InquiryView> findByUserId(
        UUID userId, InquiryStatus status, String keyword, int limit, int offset
    ) {
        return mapper.selectByUserId(
                userId, status == null ? null : status.name(), keyword, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByUserId(UUID userId, InquiryStatus status, String keyword) {
        return mapper.countByUserId(
            userId, status == null ? null : status.name(), keyword);
    }

    @Override
    public List<InquiryView> findByInstructorUserId(
        UUID instructorUserId, InquiryStatus status, UUID courseId, int limit, int offset
    ) {
        return mapper.selectByInstructorUserId(
                instructorUserId, status == null ? null : status.name(),
                courseId, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByInstructorUserId(
        UUID instructorUserId, InquiryStatus status, UUID courseId
    ) {
        return mapper.countByInstructorUserId(
            instructorUserId, status == null ? null : status.name(), courseId);
    }

    @Override
    public List<InquiryView> findByInstitutionId(
        UUID institutionId, InquiryStatus status, UUID courseId, int limit, int offset
    ) {
        return mapper.selectByInstitutionId(
                institutionId, status == null ? null : status.name(),
                courseId, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByInstitutionId(
        UUID institutionId, InquiryStatus status, UUID courseId
    ) {
        return mapper.countByInstitutionId(
            institutionId, status == null ? null : status.name(), courseId);
    }

    @Override
    public List<InquiryView> findByCourseId(UUID courseId, InquiryType type, int limit, int offset) {
        return mapper.selectByCourseId(
                courseId, type == null ? null : type.name(), limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByCourseId(UUID courseId, InquiryType type) {
        return mapper.countByCourseId(courseId, type == null ? null : type.name());
    }

    @Override
    public List<InquiryView> findForAdmin(
        InquiryStatus status, InquiryType inquiryType, CourseEducationType educationType,
        UUID institutionId, UUID courseId, String keyword, int limit, int offset
    ) {
        return mapper.selectForAdmin(
                status == null ? null : status.name(),
                inquiryType == null ? null : inquiryType.name(),
                educationType == null ? null : educationType.name(),
                institutionId, courseId, keyword, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countForAdmin(
        InquiryStatus status, InquiryType inquiryType, CourseEducationType educationType,
        UUID institutionId, UUID courseId, String keyword
    ) {
        return mapper.countForAdmin(
            status == null ? null : status.name(),
            inquiryType == null ? null : inquiryType.name(),
            educationType == null ? null : educationType.name(),
            institutionId, courseId, keyword);
    }

    private InquiryRow toRow(Inquiry inquiry) {
        return new InquiryRow(
            inquiry.id(), inquiry.userId(), inquiry.courseId(), inquiry.title(),
            inquiry.content(), inquiry.status().name(), inquiry.answer(),
            inquiry.answeredBy(), inquiry.answeredAt(),
            inquiry.createdAt(), inquiry.updatedAt(), inquiry.type().name());
    }

    private Inquiry toDomain(InquiryRow row) {
        return new Inquiry(
            row.id(), row.userId(), row.courseId(), row.title(), row.content(),
            InquiryStatus.valueOf(row.status()), row.answer(), row.answeredBy(),
            row.answeredAt(), row.createdAt(), row.updatedAt(),
            InquiryType.valueOf(row.type()));
    }

    private InquiryView toView(InquiryViewRow row) {
        EnrolleeProfile profile = new EnrolleeProfile(
            row.enrolleeName(), row.enrolleeBirthDate(),
            row.enrolleeGender() == null ? null : Gender.valueOf(row.enrolleeGender()),
            row.enrolleeEmail(), row.enrolleePhone(), row.enrolleeProfileImageId());
        return new InquiryView(
            row.id(), row.userId(), row.courseId(), row.title(),
            InquiryStatus.valueOf(row.status()), InquiryType.valueOf(row.inquiryType()),
            row.createdAt(), row.courseTitle(), profile, row.institutionName(),
            row.instructorName(), row.classificationCode(), row.educationType(),
            row.sessionCount(), row.tags(),
            row.tuitionType() == null ? null : CourseFeeType.valueOf(row.tuitionType()),
            row.tuitionAmount(), row.thumbnailFileId());
    }
}
