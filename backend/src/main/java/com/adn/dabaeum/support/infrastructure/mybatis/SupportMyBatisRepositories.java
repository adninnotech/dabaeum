package com.adn.dabaeum.support.infrastructure.mybatis;

import com.adn.dabaeum.support.domain.CommonCode;
import com.adn.dabaeum.support.domain.CommonCodeRepository;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeRepository;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsContentRepository;
import com.adn.dabaeum.support.domain.TermsType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class SupportMyBatisRepositories
    implements NoticeRepository, TermsContentRepository, CommonCodeRepository {

    private final SupportMapper mapper;

    public SupportMyBatisRepositories(SupportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Notice notice) {
        mapper.insertNotice(toRow(notice));
    }

    @Override
    public Optional<Notice> findById(UUID id) {
        return Optional.ofNullable(mapper.selectNoticeById(id)).map(this::toDomain);
    }

    @Override
    public List<Notice> findPublishedPage(NoticeAudience audience, int limit, int offset) {
        return mapper.selectPublishedNotices(
                audience == null ? null : audience.name(), limit, offset)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public long countPublished(NoticeAudience audience) {
        return mapper.countPublishedNotices(audience == null ? null : audience.name());
    }

    @Override
    public List<Notice> findAdminPage(int limit, int offset) {
        return mapper.selectAdminNotices(limit, offset)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public long countAll() {
        return mapper.countAllNotices();
    }

    @Override
    public boolean update(Notice notice) {
        return mapper.updateNotice(toRow(notice)) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return mapper.deleteNotice(id) == 1;
    }

    @Override
    public Optional<TermsContent> findLatestByType(TermsType type) {
        return Optional.ofNullable(mapper.selectLatestTermsByType(type.name()))
            .map(row -> new TermsContent(
                row.id(), TermsType.valueOf(row.type()), row.title(), row.body(),
                row.version(), row.createdAt(), row.updatedAt()));
    }

    @Override
    public List<CommonCode> findByGroup(String codeGroup) {
        return mapper.selectCodesByGroup(codeGroup).stream()
            .map(row -> new CommonCode(
                row.id(), row.codeGroup(), row.code(), row.name(),
                row.sortOrder() == null ? 0 : row.sortOrder()))
            .toList();
    }

    private NoticeRow toRow(Notice notice) {
        return new NoticeRow(
            notice.id(), notice.title(), notice.body(), notice.audience().name(),
            notice.status().name(), notice.publishedAt(), notice.createdBy(),
            notice.createdAt(), notice.updatedAt());
    }

    private Notice toDomain(NoticeRow row) {
        return new Notice(
            row.id(), row.title(), row.body(),
            NoticeAudience.valueOf(row.audience()),
            NoticeStatus.valueOf(row.status()),
            row.publishedAt(), row.createdBy(), row.createdAt(), row.updatedAt());
    }
}
