package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.support.application.SupportApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class SupportController {

    private final SupportApplicationService service;
    private final SupportApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public SupportController(
        SupportApplicationService service,
        SupportApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/notices")
    public NoticePageResponse listNotices(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String audience,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listPublishedNotices(
            mapper.parseAudience(audience, false), page, size);
        return mapper.toPublicPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/notices/{noticeId}")
    public ApiResponse<NoticeResponse> getNotice(
        @PathVariable UUID noticeId,
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            mapper.toResponse(service.getPublishedNotice(noticeId)),
            mapper.meta(requestId(servletRequest)));
    }

    @GetMapping("/admin/notices")
    public AdminNoticePageResponse listAdminNotices(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listAdminNotices(
            currentUserProvider.requireContext(), page, size);
        return mapper.toAdminPageResponse(result, requestId(servletRequest));
    }

    @PostMapping("/admin/notices")
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(
        @RequestBody(required = false) NoticeCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        var notice = service.createNotice(mapper.toCreateCommand(
            request, currentUserProvider.requireContext()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/notices/" + notice.id()))
            .body(new ApiResponse<>(
                mapper.toResponse(notice), mapper.meta(requestId(servletRequest))));
    }

    @PutMapping("/admin/notices/{noticeId}")
    public ApiResponse<NoticeResponse> updateNotice(
        @PathVariable UUID noticeId,
        @RequestBody(required = false) NoticeUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        var notice = service.updateNotice(mapper.toUpdateCommand(
            noticeId, request, currentUserProvider.requireContext()));
        return new ApiResponse<>(
            mapper.toResponse(notice), mapper.meta(requestId(servletRequest)));
    }

    @DeleteMapping("/admin/notices/{noticeId}")
    public ApiResponse<com.adn.dabaeum.common.api.DeletionResponse> deleteNotice(
        @PathVariable UUID noticeId,
        HttpServletRequest servletRequest
    ) {
        service.deleteNotice(noticeId, currentUserProvider.requireContext());
        return new ApiResponse<>(
            new com.adn.dabaeum.common.api.DeletionResponse(noticeId),
            mapper.meta(requestId(servletRequest)));
    }

    @GetMapping("/support/faqs")
    public FaqPageResponse listFaqs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        return mapper.toFaqPageResponse(
            service.listFaqs(page, size), requestId(servletRequest));
    }

    @GetMapping("/contents/terms")
    public ApiResponse<TermsResponse> getTerms(
        @RequestParam String type,
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            mapper.toResponse(service.getTerms(mapper.parseTermsType(type))),
            mapper.meta(requestId(servletRequest)));
    }

    @GetMapping("/codes")
    public CommonCodeListResponse listCodes(
        @RequestParam String group,
        HttpServletRequest servletRequest
    ) {
        if (group == null || group.isBlank() || group.trim().length() > 50) {
            throw new com.adn.dabaeum.common.api.ApiException(
                HttpStatus.BAD_REQUEST,
                com.adn.dabaeum.common.api.ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid",
                java.util.List.of("group"));
        }
        return mapper.toCodeListResponse(
            service.listCodes(group.trim()), requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
