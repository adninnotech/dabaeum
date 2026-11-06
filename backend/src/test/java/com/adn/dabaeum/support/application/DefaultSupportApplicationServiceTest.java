package com.adn.dabaeum.support.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DefaultSupportApplicationServiceTest {

    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private final FaqRepository faqRepository = mock(FaqRepository.class);
    private final TermsContentRepository termsRepository = mock(TermsContentRepository.class);
    private final CommonCodeRepository codeRepository = mock(CommonCodeRepository.class);
    private final AuthorizationPolicy authorizationPolicy = new AuthorizationPolicy();
    private final Clock clock = Clock.fixed(
        Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);

    private DefaultSupportApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSupportApplicationService(
            noticeRepository, faqRepository, termsRepository, codeRepository,
            authorizationPolicy, clock);
    }

    @Test
    void adminNoticeDetailReturnsDraftsThatThePublicDetailHides() {
        UUID noticeId = UUID.randomUUID();
        Notice draft = new Notice(noticeId, "초안", "본문", NoticeAudience.PUBLIC, NoticeStatus.DRAFT,
            null, null, null, clock.instant(), clock.instant());
        when(noticeRepository.findById(noticeId)).thenReturn(Optional.of(draft));

        assertThat(service.getAdminNotice(noticeId, platformAdmin())).isEqualTo(draft);
        // 공개 상세는 초안을 404 로 숨긴다. 관리자 수정 화면이 이걸 불러 초안을 못 열었다(QA OPS-01).
        assertThatThrownBy(() -> service.getPublishedNotice(noticeId))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.NOTICE_NOT_FOUND));
    }

    @Test
    void adminNoticeDetailRequiresPlatformAdminAndMapsMissingTo404() {
        UUID noticeId = UUID.randomUUID();
        when(noticeRepository.findById(noticeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAdminNotice(noticeId, learner()))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.getAdminNotice(noticeId, platformAdmin()))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.NOTICE_NOT_FOUND));
    }

    @Test
    void createFaqRequiresPlatformAdmin() {
        FaqCommand command = new FaqCommand(
            null, "질문", "답변", 0, learner(), clock.instant());

        assertThatThrownBy(() -> service.createFaq(command))
            .isInstanceOf(ApiException.class)
            .satisfies(exception ->
                assertThat(((ApiException) exception).code())
                    .isEqualTo(ApiErrorCode.FORBIDDEN));
    }

    @Test
    void createFaqSavesAndReturnsTheFaq() {
        FaqCommand command = new FaqCommand(
            null, "질문", "답변", 3, platformAdmin(), clock.instant());

        Faq created = service.createFaq(command);

        assertThat(created.question()).isEqualTo("질문");
        assertThat(created.answer()).isEqualTo("답변");
        assertThat(created.sortOrder()).isEqualTo(3);
        verify(faqRepository, times(1)).save(any());
    }

    @Test
    void updateFaqThrowsNotFoundWhenMissing() {
        UUID faqId = UUID.randomUUID();
        when(faqRepository.findById(faqId)).thenReturn(Optional.empty());
        FaqCommand command = new FaqCommand(
            faqId, "질문", "답변", 0, platformAdmin(), clock.instant());

        assertThatThrownBy(() -> service.updateFaq(command))
            .isInstanceOf(ApiException.class)
            .satisfies(exception ->
                assertThat(((ApiException) exception).code())
                    .isEqualTo(ApiErrorCode.FAQ_NOT_FOUND));
    }

    @Test
    void updateFaqPreservesCreatedAt() {
        UUID faqId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Faq existing = new Faq(faqId, "기존질문", "기존답변", 1, createdAt, createdAt);
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(existing));
        when(faqRepository.update(any())).thenReturn(true);
        FaqCommand command = new FaqCommand(
            faqId, "새질문", "새답변", 2, platformAdmin(), clock.instant());

        Faq updated = service.updateFaq(command);

        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isEqualTo(clock.instant());
        assertThat(updated.question()).isEqualTo("새질문");
    }

    @Test
    void deleteFaqThrowsNotFoundWhenMissing() {
        UUID faqId = UUID.randomUUID();
        when(faqRepository.delete(faqId)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteFaq(faqId, platformAdmin()))
            .isInstanceOf(ApiException.class)
            .satisfies(exception ->
                assertThat(((ApiException) exception).code())
                    .isEqualTo(ApiErrorCode.FAQ_NOT_FOUND));
    }

    @Test
    void createTermsRequiresPlatformAdmin() {
        TermsContentCommand command = new TermsContentCommand(
            TermsType.USAGE, "제목", "본문", "1.0", learner(), clock.instant());

        assertThatThrownBy(() -> service.createTerms(command))
            .isInstanceOf(ApiException.class)
            .satisfies(exception ->
                assertThat(((ApiException) exception).code())
                    .isEqualTo(ApiErrorCode.FORBIDDEN));
    }

    @Test
    void createTermsSavesTheNewVersion() {
        TermsContentCommand command = new TermsContentCommand(
            TermsType.USAGE, "이용약관", "본문", "1.2", platformAdmin(), clock.instant());

        TermsContent created = service.createTerms(command);

        assertThat(created.version()).isEqualTo("1.2");
        assertThat(created.type()).isEqualTo(TermsType.USAGE);
        verify(termsRepository, times(1)).save(any());
    }

    @Test
    void createTermsMapsDuplicateVersionToConflict() {
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
            .when(termsRepository).save(any());
        TermsContentCommand command = new TermsContentCommand(
            TermsType.USAGE, "이용약관", "본문", "1.0", platformAdmin(), clock.instant());

        assertThatThrownBy(() -> service.createTerms(command))
            .isInstanceOf(ApiException.class)
            .satisfies(exception ->
                assertThat(((ApiException) exception).code())
                    .isEqualTo(ApiErrorCode.TERMS_CONTENT_CONFLICT));
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
