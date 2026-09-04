package com.adn.dabaeum.instructor.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.instructor.application.InstructorApplicationService;
import com.adn.dabaeum.instructor.application.InstructorApplicationView;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class InstructorApplicationController {

    private final InstructorApplicationService service;
    private final InstructorApplicationApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public InstructorApplicationController(
        InstructorApplicationService service,
        InstructorApplicationApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/institutions/{institutionId}/instructor-applications")
    public ResponseEntity<ApiResponse<InstructorApplicationResponse>> apply(
        @PathVariable UUID institutionId,
        @Valid @RequestBody(required = false) InstructorApplicationRequest request,
        HttpServletRequest servletRequest
    ) {
        InstructorApplicationView result = service.apply(
            mapper.toApplyCommand(institutionId, request),
            currentUserProvider.requireContext()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/instructor-applications/{applicationId}")
            .buildAndExpand(result.id())
            .toUri();
        return ResponseEntity.created(location)
            .body(mapper.toApiResponse(result, requestId(servletRequest)));
    }

    @GetMapping("/instructor-applications/me")
    public InstructorApplicationPageResponse listMine(
        @RequestParam(required = false) InstructorApplicationStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "appliedAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        return mapper.toPageResponse(
            service.listMine(
                mapper.toListQuery(null, status, page, size, sort),
                currentUserProvider.requireContext()
            ),
            requestId(servletRequest)
        );
    }

    @GetMapping("/instructor-applications/{applicationId}")
    public ApiResponse<InstructorApplicationResponse> get(
        @PathVariable UUID applicationId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.get(applicationId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    @GetMapping("/institutions/{institutionId}/instructor-applications")
    public InstructorApplicationPageResponse listInstitution(
        @PathVariable UUID institutionId,
        @RequestParam(required = false) InstructorApplicationStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "appliedAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        return mapper.toPageResponse(
            service.listInstitution(
                mapper.toListQuery(institutionId, status, page, size, sort),
                currentUserProvider.requireContext()
            ),
            requestId(servletRequest)
        );
    }

    @PostMapping("/instructor-applications/{applicationId}/approve")
    public ApiResponse<InstructorApplicationResponse> approve(
        @PathVariable UUID applicationId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.approve(applicationId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    @PostMapping("/instructor-applications/{applicationId}/reject")
    public ApiResponse<InstructorApplicationResponse> reject(
        @PathVariable UUID applicationId,
        @Valid @RequestBody InstructorApplicationRejectRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.reject(
                mapper.toRejectCommand(applicationId, request),
                currentUserProvider.requireContext()
            ),
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
