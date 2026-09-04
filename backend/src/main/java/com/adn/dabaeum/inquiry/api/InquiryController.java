package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class InquiryController {

    private final InquiryApplicationService service;
    private final InquiryApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public InquiryController(
        InquiryApplicationService service,
        InquiryApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/inquiries")
    public ResponseEntity<ApiResponse<InquiryResponse>> create(
        @RequestBody(required = false) InquiryCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        var inquiry = service.create(mapper.toCreateCommand(
            request, currentUserProvider.requireContext()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/inquiries/" + inquiry.id()))
            .body(new ApiResponse<>(
                mapper.toResponse(inquiry), mapper.meta(requestId(servletRequest))));
    }

    @GetMapping("/inquiries/{inquiryId}")
    public ApiResponse<InquiryResponse> get(
        @PathVariable UUID inquiryId,
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            mapper.toResponse(service.get(
                inquiryId, currentUserProvider.requireContext())),
            mapper.meta(requestId(servletRequest)));
    }

    @PostMapping("/inquiries/{inquiryId}/reply")
    public ApiResponse<InquiryResponse> reply(
        @PathVariable UUID inquiryId,
        @RequestBody(required = false) InquiryReplyRequest request,
        HttpServletRequest servletRequest
    ) {
        var inquiry = service.reply(mapper.toReplyCommand(
            inquiryId, request, currentUserProvider.requireContext()));
        return new ApiResponse<>(
            mapper.toResponse(inquiry), mapper.meta(requestId(servletRequest)));
    }

    @GetMapping("/users/me/inquiries")
    public InquiryPageResponse listMine(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listMine(
            currentUserProvider.requireContext(), mapper.parseStatus(status), page, size);
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/instructors/me/inquiries")
    public InquiryPageResponse listForInstructor(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID courseId,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listForInstructor(
            currentUserProvider.requireContext(), mapper.parseStatus(status),
            courseId, page, size);
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/institutions/{institutionId}/inquiries")
    public InquiryPageResponse listForInstitution(
        @PathVariable UUID institutionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID courseId,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listForInstitution(
            currentUserProvider.requireContext(), institutionId,
            mapper.parseStatus(status), courseId, page, size);
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
