package com.adn.dabaeum.completion.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.completion.application.CompletionApplicationService;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.correction.api.CorrectionReasonRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CompletionController {

    private final CompletionApplicationService service;
    private final CompletionApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public CompletionController(
        CompletionApplicationService service,
        CompletionApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/enrollments/{enrollmentId}/completion")
    public ApiResponse<CompletionResponse> getCompletion(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        Completion completion = service.get(
            enrollmentId, currentUserProvider.requireContext());
        return mapper.toResponse(completion, requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/completion/evaluate")
    public ApiResponse<CompletionResponse> evaluateCompletion(
        @PathVariable UUID enrollmentId,
        @Valid @RequestBody CompletionEvaluationRequest requestBody,
        HttpServletRequest request
    ) {
        Completion completion = service.evaluate(
            mapper.toEvaluateCommand(enrollmentId, requestBody),
            currentUserProvider.requireContext());
        return mapper.toResponse(completion, requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/completion/confirm")
    public ApiResponse<CompletionResponse> confirmCompletion(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        Completion completion = service.confirm(
            enrollmentId, currentUserProvider.requireContext());
        return mapper.toResponse(completion, requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/completion/revert")
    public ApiResponse<CompletionResponse> revertCompletion(
        @PathVariable UUID enrollmentId,
        @Valid @RequestBody CorrectionReasonRequest body,
        HttpServletRequest request
    ) {
        Completion reverted = service.revertConfirmation(
            enrollmentId, body.reason(), currentUserProvider.requireContext());
        return mapper.toResponse(reverted, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
