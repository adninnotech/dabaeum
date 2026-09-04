package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.CredentialDocumentDownloadService;
import com.adn.dabaeum.credential.application.CredentialPage;
import com.adn.dabaeum.credential.application.CredentialVerificationPage;
import com.adn.dabaeum.credential.application.CredentialVerificationService;
import com.adn.dabaeum.credential.application.IssueCredentialCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CredentialController {

    private final CredentialApplicationService service;
    private final CredentialVerificationService verificationService;
    private final CredentialApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final CredentialDocumentDownloadService documentDownloadService;

    public CredentialController(
        CredentialApplicationService service,
        CredentialVerificationService verificationService,
        CredentialApiMapper mapper,
        CurrentUserProvider currentUserProvider,
        CredentialDocumentDownloadService documentDownloadService
    ) {
        this.service = service;
        this.verificationService = verificationService;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.documentDownloadService = documentDownloadService;
    }

    @PostMapping("/completions/{completionId}/credentials")
    public ResponseEntity<ApiResponse<CredentialResponse>> issue(
        @PathVariable UUID completionId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) CredentialIssueRequest request,
        HttpServletRequest servletRequest
    ) {
        requireIdempotencyKey(idempotencyKey);
        AuthenticatedUserContext actor = currentUserProvider.requireContext();
        IssueCredentialCommand command = mapper.toIssueCommand(
            completionId, request, idempotencyKey, actor);
        var credential = service.issue(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .location(URI.create("/api/v1/credentials/" + credential.id()))
            .body(mapper.toApiResponse(credential, requestId(servletRequest)));
    }

    @GetMapping("/credentials/{credentialId}")
    public ApiResponse<CredentialDetailResponse> get(
        @PathVariable UUID credentialId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toDetailApiResponse(
            service.get(credentialId, currentUserProvider.requireContext()),
            requestId(servletRequest));
    }

    @GetMapping(value = "/credentials/{credentialId}/document",
        produces = "application/vc+jwt")
    public ResponseEntity<String> downloadDocument(
        @PathVariable UUID credentialId
    ) {
        CredentialDocumentDownloadService.Download download = documentDownloadService.download(
            credentialId, currentUserProvider.requireContext());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vc+jwt"))
            .cacheControl(CacheControl.noStore())
            .header("Content-Disposition", ContentDisposition.attachment()
                .filename(download.credentialNo() + ".jwt").build().toString())
            .body(download.compactJws());
    }

    @PostMapping("/credentials/{credentialId}/revoke")
    public ResponseEntity<ApiResponse<CredentialResponse>> revoke(
        @PathVariable UUID credentialId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) CredentialRevokeRequest request,
        HttpServletRequest servletRequest
    ) {
        requireIdempotencyKey(idempotencyKey);
        AuthenticatedUserContext actor = currentUserProvider.requireContext();
        var credential = service.revoke(
            mapper.toRevokeCommand(credentialId, request, idempotencyKey, actor));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .location(URI.create("/api/v1/credentials/" + credentialId))
            .body(mapper.toApiResponse(credential, requestId(servletRequest)));
    }

    @PostMapping("/credentials/{credentialId}/reissue")
    public ResponseEntity<ApiResponse<CredentialResponse>> reissue(
        @PathVariable UUID credentialId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) CredentialReissueRequest request,
        HttpServletRequest servletRequest
    ) {
        requireIdempotencyKey(idempotencyKey);
        AuthenticatedUserContext actor = currentUserProvider.requireContext();
        var credential = service.reissue(
            mapper.toReissueCommand(credentialId, request, idempotencyKey, actor));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .location(URI.create("/api/v1/credentials/" + credential.id()))
            .body(mapper.toApiResponse(credential, requestId(servletRequest)));
    }

    @PostMapping("/credentials/verify")
    public ApiResponse<CredentialVerificationResponse> verify(
        @RequestBody CredentialVerifyRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        return mapper.toVerificationResponse(
            verificationService.verify(mapper.toVerifyCommand(request, requestId)), requestId);
    }

    @GetMapping("/credentials/{credentialId}/verifications")
    public CredentialVerificationPageResponse listVerifications(
        @PathVariable UUID credentialId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "verifiedAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        CredentialApiMapper.CredentialVerificationPageQuery query =
            mapper.toVerificationPageQuery(page, size, sort);
        CredentialVerificationPage result = verificationService.list(
            credentialId, query.page(), query.size(), query.sort(), currentUserProvider.requireContext());
        return mapper.toVerificationPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/users/{userId}/credentials")
    public CredentialPageResponse listByUser(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        CredentialPage result = service.listByUser(mapper.toListQuery(
            userId, page, size, sort, currentUserProvider.requireContext()));
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/users/me/credentials")
    public CredentialPageResponse listCurrentUserCredentials(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        AuthenticatedUserContext actor = currentUserProvider.requireContext();
        CredentialPage result = service.listByUser(mapper.toListQuery(
            actor.userId(), page, size, sort, actor));
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    private void requireIdempotencyKey(String value) {
        if (value == null || value.trim().length() < 8 || value.trim().length() > 128) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Idempotency-Key header is invalid",
                List.of("Idempotency-Key"));
        }
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
