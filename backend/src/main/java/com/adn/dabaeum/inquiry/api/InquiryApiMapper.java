package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.inquiry.application.CreateInquiryCommand;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.application.ReplyInquiryCommand;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InquiryApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public InquiryApiMapper(Clock clock) {
        this.clock = clock;
    }

    public void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
    }

    public InquiryStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InquiryStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("status"));
        }
    }

    public CreateInquiryCommand toCreateCommand(
        InquiryCreateRequest request, AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of("title", "content"));
        }
        List<String> invalid = new ArrayList<>();
        String title = request.title() == null ? null : request.title().trim();
        String content = request.content() == null ? null : request.content().trim();
        if (title == null || title.isEmpty() || title.length() > 200) {
            invalid.add("title");
        }
        if (content == null || content.isEmpty()) {
            invalid.add("content");
        }
        if (!invalid.isEmpty()) {
            throw validation(invalid);
        }
        return new CreateInquiryCommand(
            title, content, request.courseId(), actor, clock.instant());
    }

    public ReplyInquiryCommand toReplyCommand(
        UUID inquiryId, InquiryReplyRequest request, AuthenticatedUserContext actor
    ) {
        String content = request == null || request.content() == null
            ? null : request.content().trim();
        if (content == null || content.isEmpty()) {
            throw validation(List.of("content"));
        }
        return new ReplyInquiryCommand(inquiryId, content, actor, clock.instant());
    }

    public InquiryResponse toResponse(Inquiry inquiry) {
        return new InquiryResponse(
            inquiry.id(), inquiry.userId(), inquiry.courseId(), inquiry.title(),
            inquiry.content(), inquiry.status(), inquiry.answer(),
            inquiry.answeredAt(), inquiry.createdAt());
    }

    public InquiryPageResponse toPageResponse(InquiryViewPage page, String requestId) {
        return new InquiryPageResponse(
            page.data().stream().map(this::toSummary).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private InquirySummaryResponse toSummary(InquiryView view) {
        return new InquirySummaryResponse(
            view.id(), view.courseId(), view.title(), view.status(),
            view.createdAt(), view.courseTitle(), view.userName());
    }

    public ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private ApiException validation(List<String> fields) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", fields);
    }
}
