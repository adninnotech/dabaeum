package com.adn.dabaeum.support.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.CommonCode;
import com.adn.dabaeum.support.domain.CommonCodeRepository;
import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.FaqRepository;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeRepository;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsContentRepository;
import com.adn.dabaeum.support.domain.TermsType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultSupportApplicationService implements SupportApplicationService {

    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;
    private final TermsContentRepository termsRepository;
    private final CommonCodeRepository codeRepository;
    private final AuthorizationPolicy authorizationPolicy;

    public DefaultSupportApplicationService(
        NoticeRepository noticeRepository,
        FaqRepository faqRepository,
        TermsContentRepository termsRepository,
        CommonCodeRepository codeRepository,
        AuthorizationPolicy authorizationPolicy
    ) {
        this.noticeRepository = noticeRepository;
        this.faqRepository = faqRepository;
        this.termsRepository = termsRepository;
        this.codeRepository = codeRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public NoticePage listPublishedNotices(NoticeAudience audience, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        List<Notice> data = noticeRepository.findPublishedPage(audience, size, offset);
        long totalElements = noticeRepository.countPublished(audience);
        return new NoticePage(data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public Notice getPublishedNotice(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
            .orElseThrow(this::noticeNotFound);
        if (notice.status() != NoticeStatus.PUBLISHED) {
            throw noticeNotFound();
        }
        return notice;
    }

    @Override
    @Transactional(readOnly = true)
    public NoticePage listAdminNotices(AuthenticatedUserContext actor, int page, int size) {
        authorizationPolicy.requirePlatformAdmin(actor);
        int offset = Math.multiplyExact(page, size);
        List<Notice> data = noticeRepository.findAdminPage(size, offset);
        long totalElements = noticeRepository.countAll();
        return new NoticePage(data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional
    public Notice createNotice(NoticeCommand command) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(command.actor());
        NoticeStatus status = command.status() == null
            ? NoticeStatus.DRAFT : command.status();
        Notice notice = new Notice(
            UUID.randomUUID(), command.title(), command.body(), command.audience(),
            status,
            status == NoticeStatus.PUBLISHED ? command.requestedAt() : null,
            command.actor().userId(), command.requestedAt(), command.requestedAt());
        noticeRepository.save(notice);
        return notice;
    }

    @Override
    @Transactional
    public Notice updateNotice(NoticeCommand command) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(command.actor());
        Notice existing = noticeRepository.findById(command.noticeId())
            .orElseThrow(this::noticeNotFound);
        NoticeStatus status = command.status() == null
            ? existing.status() : command.status();
        Notice updated = new Notice(
            existing.id(), command.title(), command.body(), command.audience(),
            status,
            status == NoticeStatus.PUBLISHED
                ? (existing.publishedAt() != null
                    ? existing.publishedAt() : command.requestedAt())
                : null,
            existing.createdBy(), existing.createdAt(), command.requestedAt());
        if (!noticeRepository.update(updated)) {
            throw noticeNotFound();
        }
        return updated;
    }

    @Override
    @Transactional
    public void deleteNotice(UUID noticeId, AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        if (!noticeRepository.delete(noticeId)) {
            throw noticeNotFound();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FaqPage listFaqs(int page, int size) {
        int offset = Math.multiplyExact(page, size);
        List<Faq> data = faqRepository.findPage(size, offset);
        long totalElements = faqRepository.countAll();
        return new FaqPage(data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public TermsContent getTerms(TermsType type) {
        return termsRepository.findLatestByType(type)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.NOTICE_NOT_FOUND,
                "Terms content not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommonCode> listCodes(String codeGroup) {
        return codeRepository.findByGroup(codeGroup);
    }

    private ApiException noticeNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOTICE_NOT_FOUND, "Notice not found");
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
