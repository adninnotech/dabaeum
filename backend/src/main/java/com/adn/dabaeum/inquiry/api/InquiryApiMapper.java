package com.adn.dabaeum.inquiry.api;

import org.springframework.beans.factory.ObjectProvider;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.text.NameMask;
import com.adn.dabaeum.enrollment.api.CourseCardFields;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.inquiry.application.CourseInquiryViewPage;
import com.adn.dabaeum.inquiry.application.CreateInquiryCommand;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.application.ReplyInquiryCommand;
import com.adn.dabaeum.inquiry.domain.Inquiry;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
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

    // 분류 서비스는 local/dev 에만 있다. 없으면(컨트롤러 테스트) 경로는 빈 배열.
    private final ObjectProvider<CourseClassificationService> classifications;

    public InquiryApiMapper(Clock clock, ObjectProvider<CourseClassificationService> classifications) {
        this.classifications = classifications;
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

    public InquiryType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return InquiryType.valueOf(type.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("type"));
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
        InquiryType type = request.type() == null ? InquiryType.COURSE : request.type();
        return new CreateInquiryCommand(
            title, content, request.courseId(), actor, clock.instant(), type);
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
            inquiry.answeredAt(), inquiry.createdAt(), inquiry.type());
    }

    public InquiryPageResponse toPageResponse(InquiryViewPage page, String requestId) {
        return new InquiryPageResponse(
            page.data().stream().map(view -> toSummary(view, true)).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    /**
     * C-6 · E-2 공유 강좌별 문의 목록. 같은 엔드포인트를 관리자와 학습자가 함께 쓴다.
     * 실명·연락처를 보여줄지는 <b>서비스가 강좌의 기관을 보고</b> 판정해 넘겨준다 —
     * 여기서 역할 이름으로 판정하면 다른 기관 강사에게도 열린다.
     */
    public InquiryPageResponse toCoursePageResponse(
        CourseInquiryViewPage result, String requestId
    ) {
        InquiryViewPage page = result.page();
        return new InquiryPageResponse(
            page.data().stream().map(view -> toSummary(view, result.manager())).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private InquirySummaryResponse toSummary(InquiryView view, boolean manager) {
        EnrolleeProfile profile = view.profile();
        return new InquirySummaryResponse(
            view.id(), view.courseId(), view.title(), view.status(), view.inquiryType(),
            view.createdAt(), view.courseTitle(),
            manager ? profile.userName() : NameMask.mask(profile.userName()),
            manager ? profile.email() : null,
            manager ? profile.phone() : null,
            manager ? profile.profileImageId() : null,
            new CourseCardFields(
                view.institutionName(), view.instructorName(), view.classificationCode(), classificationPath(view.classificationCode()),
                view.educationType(), view.sessionCount(), view.tags(),
                view.tuitionType(), view.tuitionAmount(), view.thumbnailFileId()));
    }

    public ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private ApiException validation(List<String> fields) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", fields);
    }
    private List<String> classificationPath(String code) {
        CourseClassificationService service = classifications.getIfAvailable();
        return service == null ? List.of() : service.pathOf(code);
    }
}

