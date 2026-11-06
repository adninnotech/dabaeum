package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.application.FaqCommand;
import com.adn.dabaeum.support.application.FaqPage;
import com.adn.dabaeum.support.application.NoticeCommand;
import com.adn.dabaeum.support.application.NoticePage;
import com.adn.dabaeum.support.application.TermsContentCommand;
import com.adn.dabaeum.support.domain.CommonCode;
import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SupportApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public SupportApiMapper(Clock clock) {
        this.clock = clock;
    }

    public void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
    }

    public NoticeAudience parseAudience(String audience, boolean required) {
        if (audience == null || audience.isBlank()) {
            if (required) {
                throw validation(List.of("audience"));
            }
            return null;
        }
        try {
            return NoticeAudience.valueOf(audience.trim());
        } catch (IllegalArgumentException exception) {
            throw badRequest(List.of("audience"));
        }
    }

    public NoticeStatus parseStatus(String status, boolean required) {
        if (status == null || status.isBlank()) {
            if (required) {
                throw validation(List.of("status"));
            }
            return null;
        }
        try {
            return NoticeStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw badRequest(List.of("status"));
        }
    }

    public TermsType parseTermsType(String type) {
        if (type == null || type.isBlank()) {
            throw badRequest(List.of("type"));
        }
        try {
            return TermsType.valueOf(type.trim());
        } catch (IllegalArgumentException exception) {
            throw badRequest(List.of("type"));
        }
    }

    public NoticeCommand toCreateCommand(
        NoticeCreateRequest request, AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of("title", "body", "audience"));
        }
        return toCommand(null, request.title(), request.body(),
            request.audience(), request.status(), request.publishedAt(), actor);
    }

    public NoticeCommand toUpdateCommand(
        UUID noticeId, NoticeUpdateRequest request, AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of("title", "body", "audience", "status"));
        }
        return toCommand(noticeId, request.title(), request.body(),
            request.audience(), request.status(), request.publishedAt(), actor);
    }

    private NoticeCommand toCommand(
        UUID noticeId, String title, String body,
        String audience, String status, Instant publishedAt, AuthenticatedUserContext actor
    ) {
        List<String> invalid = new ArrayList<>();
        String trimmedTitle = title == null ? null : title.trim();
        String trimmedBody = body == null ? null : body.trim();
        // 화면과 계약은 60자다. 컬럼은 200 이라 그보다 긴 기존 행도 읽기는 그대로 된다.
        if (trimmedTitle == null || trimmedTitle.isEmpty() || trimmedTitle.length() > 60) {
            invalid.add("title");
        }
        if (trimmedBody == null || trimmedBody.isEmpty()) {
            invalid.add("body");
        }
        NoticeAudience parsedAudience = null;
        try {
            parsedAudience = NoticeAudience.valueOf(
                audience == null ? "" : audience.trim());
        } catch (IllegalArgumentException exception) {
            invalid.add("audience");
        }
        NoticeStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = NoticeStatus.valueOf(status.trim());
            } catch (IllegalArgumentException exception) {
                invalid.add("status");
            }
        }
        if (!invalid.isEmpty()) {
            throw validation(invalid);
        }
        return new NoticeCommand(
            noticeId, trimmedTitle, trimmedBody, parsedAudience, parsedStatus,
            publishedAt, actor, clock.instant());
    }

    public FaqCommand toCreateCommand(FaqCreateRequest request, AuthenticatedUserContext actor) {
        if (request == null) {
            throw validation(List.of("question", "answer"));
        }
        return toFaqCommand(null, request.question(), request.answer(),
            request.sortOrder(), actor);
    }

    public FaqCommand toUpdateCommand(
        UUID faqId, FaqUpdateRequest request, AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of("question", "answer"));
        }
        return toFaqCommand(faqId, request.question(), request.answer(),
            request.sortOrder(), actor);
    }

    private FaqCommand toFaqCommand(
        UUID faqId, String question, String answer, Integer sortOrder,
        AuthenticatedUserContext actor
    ) {
        List<String> invalid = new ArrayList<>();
        String trimmedQuestion = question == null ? null : question.trim();
        String trimmedAnswer = answer == null ? null : answer.trim();
        if (trimmedQuestion == null || trimmedQuestion.isEmpty()
            || trimmedQuestion.length() > 500) {
            invalid.add("question");
        }
        if (trimmedAnswer == null || trimmedAnswer.isEmpty()) {
            invalid.add("answer");
        }
        if (!invalid.isEmpty()) {
            throw validation(invalid);
        }
        return new FaqCommand(
            faqId, trimmedQuestion, trimmedAnswer,
            sortOrder == null ? 0 : sortOrder, actor, clock.instant());
    }

    public TermsContentCommand toCreateCommand(
        TermsContentCreateRequest request, AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of("type", "title", "body", "version"));
        }
        List<String> invalid = new ArrayList<>();
        TermsType type = null;
        try {
            type = TermsType.valueOf(
                request.type() == null ? "" : request.type().trim());
        } catch (IllegalArgumentException exception) {
            invalid.add("type");
        }
        String trimmedTitle = request.title() == null ? null : request.title().trim();
        if (trimmedTitle == null || trimmedTitle.isEmpty() || trimmedTitle.length() > 200) {
            invalid.add("title");
        }
        String trimmedBody = request.body() == null ? null : request.body().trim();
        if (trimmedBody == null || trimmedBody.isEmpty()) {
            invalid.add("body");
        }
        String trimmedVersion = request.version() == null ? null : request.version().trim();
        if (trimmedVersion == null || trimmedVersion.isEmpty() || trimmedVersion.length() > 30) {
            invalid.add("version");
        }
        if (!invalid.isEmpty()) {
            throw validation(invalid);
        }
        return new TermsContentCommand(
            type, trimmedTitle, trimmedBody, trimmedVersion, actor, clock.instant());
    }

    public NoticePageResponse toPublicPageResponse(NoticePage page, String requestId) {
        return new NoticePageResponse(
            page.data().stream()
                .map(notice -> new NoticeSummaryResponse(
                    notice.id(), notice.title(), notice.publishedAt(), notice.authorName()))
                .toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public AdminNoticePageResponse toAdminPageResponse(NoticePage page, String requestId) {
        return new AdminNoticePageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public NoticeResponse toResponse(Notice notice) {
        return new NoticeResponse(
            notice.id(), notice.title(), notice.body(), notice.audience(),
            notice.status(), notice.publishedAt(), notice.authorName(),
            notice.createdAt(), notice.updatedAt());
    }

    public FaqPageResponse toFaqPageResponse(FaqPage page, String requestId) {
        return new FaqPageResponse(
            page.data().stream()
                .map(this::toResponse)
                .toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public FaqResponse toResponse(Faq faq) {
        return new FaqResponse(faq.id(), faq.question(), faq.answer(), faq.sortOrder());
    }

    public TermsResponse toResponse(TermsContent terms) {
        return new TermsResponse(
            terms.type(), terms.title(), terms.body(), terms.version(), terms.updatedAt());
    }

    public CommonCodeListResponse toCodeListResponse(
        List<CommonCode> codes, String requestId
    ) {
        return new CommonCodeListResponse(
            codes.stream()
                .map(code -> new CommonCodeResponse(
                    code.code(), code.name(), code.sortOrder()))
                .toList(),
            meta(requestId));
    }

    public ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private ApiException validation(List<String> fields) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", fields);
    }

    private ApiException badRequest(List<String> fields) {
        return new ApiException(
            HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid", fields);
    }
}
