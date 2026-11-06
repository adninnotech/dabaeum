package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.KmsOperationAdjudicationService;
import com.adn.dabaeum.credential.application.SigningKeyCommands;
import com.adn.dabaeum.credential.application.SigningKeyCompromiseService;
import com.adn.dabaeum.credential.application.SigningKeyException;
import com.adn.dabaeum.credential.application.SigningKeyLifecycleService;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningService;
import com.adn.dabaeum.credential.application.SigningKeyRotationService;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기관 서명키 관리 API (설계서 §12.1). 모든 경로는 PLATFORM_ADMIN 전용 (ApiAuthorizationRules). */
@RestController
@RequestMapping("/api/v1/institutions/{institutionId}/signing-keys")
public class InstitutionSigningKeyController {

    private final SigningKeyProvisioningService provisioning;
    private final SigningKeyRotationService rotation;
    private final SigningKeyLifecycleService lifecycle;
    private final SigningKeyCompromiseService compromiseService;
    private final KmsOperationAdjudicationService adjudication;
    private final SigningKeyApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public InstitutionSigningKeyController(
        SigningKeyProvisioningService provisioning,
        SigningKeyRotationService rotation,
        SigningKeyLifecycleService lifecycle,
        SigningKeyCompromiseService compromiseService,
        KmsOperationAdjudicationService adjudication,
        SigningKeyApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.provisioning = provisioning;
        this.rotation = rotation;
        this.lifecycle = lifecycle;
        this.compromiseService = compromiseService;
        this.adjudication = adjudication;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SigningKeyOverviewResponse>> create(
        @PathVariable UUID institutionId,
        @Valid @RequestBody SigningKeyCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> provisioning.createKeys(new SigningKeyCommands.CreateKeys(
            institutionId, requester, request.idempotencyKey(), request.reason())));
        return ResponseEntity.status(HttpStatus.CREATED).body(overview(institutionId, servletRequest));
    }

    @GetMapping
    public ApiResponse<SigningKeyOverviewResponse> get(
        @PathVariable UUID institutionId,
        HttpServletRequest servletRequest
    ) {
        actor();
        return overview(institutionId, servletRequest);
    }

    @PostMapping("/approvals")
    public ResponseEntity<ApiResponse<SigningKeyOverviewResponse.Approval>> approve(
        @PathVariable UUID institutionId,
        @Valid @RequestBody SigningKeyApprovalRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID approver = actor().userId();
        KmsApproval approval = run(() -> lifecycle.createApproval(new SigningKeyCommands.CreateApproval(
            institutionId, SigningKeyApiMapper.purpose(request.purpose()), action(request.action()), approver,
            request.idempotencyKey(), request.keyVersion(), request.compromisedAt(), request.reason())));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toApiResponse(approval, requestId(servletRequest)));
    }

    @PostMapping("/{purpose}/rotate")
    public ApiResponse<SigningKeyOverviewResponse> rotate(
        @PathVariable UUID institutionId,
        @PathVariable String purpose,
        @Valid @RequestBody SigningKeyRotateRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> rotation.rotate(new SigningKeyCommands.Rotate(institutionId,
            SigningKeyApiMapper.purpose(purpose), requester, request.approvalId(), request.idempotencyKey())));
        return overview(institutionId, servletRequest);
    }

    @PostMapping("/credential/suspend")
    public ApiResponse<SigningKeyOverviewResponse> suspend(
        @PathVariable UUID institutionId,
        @Valid @RequestBody SigningKeySuspendRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> lifecycle.suspend(new SigningKeyCommands.Suspend(
            institutionId, requester, request.idempotencyKey(), request.reason())));
        return overview(institutionId, servletRequest);
    }

    @PostMapping("/credential/resume")
    public ApiResponse<SigningKeyOverviewResponse> resume(
        @PathVariable UUID institutionId,
        @Valid @RequestBody SigningKeyResumeRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> lifecycle.resume(new SigningKeyCommands.Resume(
            institutionId, requester, request.approvalId(), request.idempotencyKey())));
        return overview(institutionId, servletRequest);
    }

    @PostMapping("/credential/revoke")
    public ApiResponse<SigningKeyOverviewResponse> revoke(
        @PathVariable UUID institutionId,
        @Valid @RequestBody SigningKeyRevokeRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> lifecycle.revoke(new SigningKeyCommands.Revoke(
            institutionId, requester, request.approvalId(), request.idempotencyKey(), request.reason())));
        return overview(institutionId, servletRequest);
    }

    @PostMapping("/{purpose}/compromise")
    public ApiResponse<SigningKeyOverviewResponse> compromise(
        @PathVariable UUID institutionId,
        @PathVariable String purpose,
        @Valid @RequestBody SigningKeyCompromiseRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> compromiseService.compromise(new SigningKeyCommands.Compromise(institutionId,
            SigningKeyApiMapper.purpose(purpose), requester, request.approvalId(), request.keyVersion(),
            request.compromisedAt(), request.idempotencyKey(), request.reason())));
        return overview(institutionId, servletRequest);
    }

    @PostMapping("/{purpose}/adjudicate")
    public ApiResponse<SigningKeyOverviewResponse> adjudicate(
        @PathVariable UUID institutionId,
        @PathVariable String purpose,
        @Valid @RequestBody SigningKeyAdjudicateRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID requester = actor().userId();
        run(() -> adjudication.adjudicate(new SigningKeyCommands.Adjudicate(institutionId,
            SigningKeyApiMapper.purpose(purpose), request.operationId(), requester, decision(request.decision()),
            request.evidence())));
        return overview(institutionId, servletRequest);
    }

    private static SigningKeyCommands.Adjudicate.Decision decision(String decision) {
        try {
            return SigningKeyCommands.Adjudicate.Decision.valueOf(decision);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "decision is invalid",
                List.of("decision"));
        }
    }

    private ApiResponse<SigningKeyOverviewResponse> overview(UUID institutionId, HttpServletRequest request) {
        return mapper.toApiResponse(run(() -> lifecycle.overview(institutionId)), requestId(request));
    }

    private AuthenticatedUserContext actor() {
        return currentUserProvider.requireContext();
    }

    private static KmsAction action(String action) {
        try {
            return KmsAction.valueOf(action);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "action is invalid",
                List.of("action"));
        }
    }

    /** 서비스 예외를 HTTP 로 옮긴다. 메시지에 비밀값이 없음은 서비스가 보장한다. */
    private static <T> T run(Supplier<T> call) {
        try {
            return call.get();
        } catch (SigningKeyException exception) {
            List<String> details = exception.operationId() == null
                ? List.of() : List.of(exception.operationId().toString());
            throw switch (exception.kind()) {
                case NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.SIGNING_KEY_NOT_FOUND,
                    exception.getMessage(), details);
                case ALREADY_EXISTS -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.SIGNING_KEY_ALREADY_EXISTS,
                    exception.getMessage(), details);
                case STATE_CONFLICT -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.SIGNING_KEY_STATE_CONFLICT,
                    exception.getMessage(), details);
                case APPROVAL_REJECTED -> new ApiException(HttpStatus.CONFLICT,
                    ApiErrorCode.SIGNING_KEY_APPROVAL_REJECTED, exception.getMessage(), details);
                case IDEMPOTENCY_CONFLICT -> new ApiException(HttpStatus.CONFLICT,
                    ApiErrorCode.SIGNING_KEY_IDEMPOTENCY_CONFLICT, exception.getMessage(), details);
                case KMS_UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.KMS_UNAVAILABLE,
                    exception.getMessage(), details);
            };
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, exception.getMessage());
        }
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
