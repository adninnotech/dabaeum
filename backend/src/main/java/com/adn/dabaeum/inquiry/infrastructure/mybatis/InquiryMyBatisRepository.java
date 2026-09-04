package com.adn.dabaeum.inquiry.infrastructure.mybatis;

import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryRepository;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryView;
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
        UUID userId, InquiryStatus status, int limit, int offset
    ) {
        return mapper.selectByUserId(
                userId, status == null ? null : status.name(), limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByUserId(UUID userId, InquiryStatus status) {
        return mapper.countByUserId(userId, status == null ? null : status.name());
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

    private InquiryRow toRow(Inquiry inquiry) {
        return new InquiryRow(
            inquiry.id(), inquiry.userId(), inquiry.courseId(), inquiry.title(),
            inquiry.content(), inquiry.status().name(), inquiry.answer(),
            inquiry.answeredBy(), inquiry.answeredAt(),
            inquiry.createdAt(), inquiry.updatedAt());
    }

    private Inquiry toDomain(InquiryRow row) {
        return new Inquiry(
            row.id(), row.userId(), row.courseId(), row.title(), row.content(),
            InquiryStatus.valueOf(row.status()), row.answer(), row.answeredBy(),
            row.answeredAt(), row.createdAt(), row.updatedAt());
    }

    private InquiryView toView(InquiryViewRow row) {
        return new InquiryView(
            row.id(), row.userId(), row.courseId(), row.title(),
            InquiryStatus.valueOf(row.status()), row.createdAt(),
            row.courseTitle(), row.userName());
    }
}
