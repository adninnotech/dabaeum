package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.credential.application.VcPublicResourceService;
import com.adn.dabaeum.credential.application.CredentialIssuerDocumentService;
import com.adn.dabaeum.credential.application.PublicCredentialStatusService;
import com.adn.dabaeum.credential.application.CredentialStatusListService;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.time.Duration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.beans.factory.ObjectProvider;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vc")
public class VcPublicResourceController {

    private static final MediaType JSON_LD = MediaType.parseMediaType("application/ld+json");

    private final VcPublicResourceService service;
    private final CredentialIssuerDocumentService issuerDocumentService;
    private final ObjectProvider<PublicCredentialStatusService> statusService;
    private final ObjectProvider<CredentialStatusListService> statusListService;

    public VcPublicResourceController(
        VcPublicResourceService service,
        CredentialIssuerDocumentService issuerDocumentService,
        ObjectProvider<PublicCredentialStatusService> statusService,
        ObjectProvider<CredentialStatusListService> statusListService
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.issuerDocumentService = Objects.requireNonNull(
            issuerDocumentService, "issuerDocumentService");
        this.statusService = Objects.requireNonNull(statusService, "statusService");
        this.statusListService = Objects.requireNonNull(statusListService, "statusListService");
    }

    @GetMapping("/contexts/lifelong-education/v1")
    public ResponseEntity<Map<String, Object>> context() {
        return ResponseEntity.ok().contentType(JSON_LD).body(service.contextDocument());
    }

    @GetMapping("/vocabulary/lifelong-education/v1")
    public ResponseEntity<Map<String, Object>> vocabulary() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(service.vocabularyDocument());
    }

    @GetMapping("/issuers/{institutionId}")
    public ResponseEntity<Map<String, Object>> issuer(
        @PathVariable UUID institutionId
    ) {
        return ResponseEntity.ok().contentType(JSON_LD)
            .body(issuerDocumentService.document(institutionId));
    }

    @GetMapping("/status/{credentialNo}")
    public ResponseEntity<PublicCredentialStatusResponse> status(
        @PathVariable String credentialNo,
        HttpServletRequest request
    ) {
        PublicCredentialStatusService available = statusService.getIfAvailable();
        if (available == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.INTERNAL_SERVER_ERROR,
                "Credential verification service is unavailable");
        }
        PublicCredentialStatusService.Status status = available.check(
            credentialNo, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(new PublicCredentialStatusResponse(
                status.credentialNo(), status.status(), status.checkedAt()));
    }

    /**
     * W3C Bitstring Status List. 리스트 하나가 수료증 최대 131,072장의 폐기 비트를 담으므로 검증자는
     * 어느 수료증을 확인하는지 드러내지 않고 조회한다. 짧게 캐시해도 되지만 폐기 반영 지연을 제한한다.
     */
    @GetMapping("/status-lists/{listId}")
    public ResponseEntity<String> statusList(@PathVariable UUID listId) {
        CredentialStatusListService available = statusListService.getIfAvailable();
        if (available == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.INTERNAL_SERVER_ERROR,
                "Credential status list service is unavailable");
        }
        SignedCredentialEnvelope envelope = available.document(listId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(envelope.mediaType()))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
            .body(envelope.compactJws());
    }
}
