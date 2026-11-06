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

@Service
@Profile({"local", "dev"})
public class DefaultSupportApplicationService implements SupportApplicationService {

    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;
    private final TermsContentRepository termsRepository;
    private final CommonCodeRepository codeRepository;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultSupportApplicationService(
        NoticeRepository noticeRepository,
        FaqRepository faqRepository,
        TermsContentRepository termsRepository,
        CommonCodeRepository codeRepository,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.noticeRepository = noticeRepository;
        this.faqRepository = faqRepository;
        this.termsRepository = termsRepository;
        this.codeRepository = codeRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public NoticePage listPublishedNotices(NoticeAudience audience, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        // 예약 게시 기준 시각. 아직 안 된 공지는 status 가 PUBLISHED 여도 목록에서 빠진다.
        Instant now = clock.instant();
        List<Notice> data = noticeRepository.findPublishedPage(audience, size, offset, now);
        long totalElements = noticeRepository.countPublished(audience, now);
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
        // 예약 게시분은 링크를 직접 열어도 아직 안 보여야 한다. 목록만 막으면 URL 로 샌다.
        if (notice.publishedAt() != null && notice.publishedAt().isAfter(clock.instant())) {
            throw noticeNotFound();
        }
        return notice;
    }

    @Override
    @Transactional(readOnly = true)
    public Notice getAdminNotice(UUID noticeId, AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        return noticeRepository.findById(noticeId).orElseThrow(this::noticeNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public NoticePage listAdminNotices(
        AuthenticatedUserContext actor, int page, int size,
        String keyword, NoticeAudience audience, NoticeStatus status
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        int offset = Math.multiplyExact(page, size);
        List<Notice> data = noticeRepository.findAdminPage(
            size, offset, keyword, audience, status);
        long totalElements = noticeRepository.countAll(keyword, audience, status);
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
            publishedAtFor(status, command.publishedAt(), null, command.requestedAt()),
            command.actor().userId(), null, command.requestedAt(), command.requestedAt());
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
            publishedAtFor(status, command.publishedAt(),
                existing.publishedAt(), command.requestedAt()),
            existing.createdBy(), existing.authorName(),
            existing.createdAt(), command.requestedAt());
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
    public FaqPage listAdminFaqs(AuthenticatedUserContext actor, int page, int size) {
        authorizationPolicy.requirePlatformAdmin(actor);
        return listFaqs(page, size);
    }

    @Override
    @Transactional
    public Faq createFaq(FaqCommand command) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(command.actor());
        Faq faq = new Faq(
            UUID.randomUUID(), command.question(), command.answer(), command.sortOrder(),
            command.requestedAt(), command.requestedAt());
        faqRepository.save(faq);
        return faq;
    }

    @Override
    @Transactional
    public Faq updateFaq(FaqCommand command) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(command.actor());
        Faq existing = faqRepository.findById(command.faqId())
            .orElseThrow(this::faqNotFound);
        Faq updated = new Faq(
            existing.id(), command.question(), command.answer(), command.sortOrder(),
            existing.createdAt(), command.requestedAt());
        if (!faqRepository.update(updated)) {
            throw faqNotFound();
        }
        return updated;
    }

    @Override
    @Transactional
    public void deleteFaq(UUID faqId, AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        if (!faqRepository.delete(faqId)) {
            throw faqNotFound();
        }
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
    @Transactional
    public TermsContent createTerms(TermsContentCommand command) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(command.actor());
        TermsContent terms = new TermsContent(
            UUID.randomUUID(), command.type(), command.title(), command.body(),
            command.version(), command.requestedAt(), command.requestedAt());
        try {
            termsRepository.save(terms);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                HttpStatus.CONFLICT, ApiErrorCode.TERMS_CONTENT_CONFLICT,
                "Terms content version already exists");
        }
        return terms;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommonCode> listCodes(String codeGroup) {
        return codeRepository.findByGroup(codeGroup);
    }

    /**
     * 게시 시각을 정한다. 요청이 명시하면 그것이 우선이라 미래를 주면 예약 게시가 된다.
     * 안 주면 기존 동작 — 이미 잡힌 시각이 있으면 유지하고, 없으면 요청 시각이다.
     * 게시 상태가 아니면 시각 자체를 두지 않는다.
     */
    private static Instant publishedAtFor(
        NoticeStatus status, Instant requested, Instant existing, Instant fallback
    ) {
        if (status != NoticeStatus.PUBLISHED) {
            return null;
        }
        if (requested != null) {
            return requested;
        }
        return existing != null ? existing : fallback;
    }

    private ApiException noticeNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOTICE_NOT_FOUND, "Notice not found");
    }

    private ApiException faqNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.FAQ_NOT_FOUND, "FAQ not found");
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
