package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.credential.application.SigningKeyOverview;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class SigningKeyApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final TypeReference<LinkedHashMap<String, String>> JWK = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SigningKeyApiMapper(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ApiResponse<SigningKeyOverviewResponse> toApiResponse(SigningKeyOverview overview, String requestId) {
        return new ApiResponse<>(new SigningKeyOverviewResponse(overview.institutionId(),
            overview.keys().stream().map(this::key).toList(),
            overview.operations().stream().map(SigningKeyApiMapper::operation).toList(),
            overview.openApprovals().stream().map(SigningKeyApiMapper::approval).toList()), meta(requestId));
    }

    public ApiResponse<SigningKeyOverviewResponse.Approval> toApiResponse(KmsApproval approval, String requestId) {
        return new ApiResponse<>(approval(approval), meta(requestId));
    }

    public static String purpose(SignKeyPurpose purpose) {
        return switch (purpose) {
            case CREDENTIAL -> "credential";
            case STATUS_LIST -> "status-list";
        };
    }

    /** 경로·본문의 purpose 표기. 모르는 값은 IllegalArgumentException (400). */
    public static SignKeyPurpose purpose(String purpose) {
        return switch (purpose == null ? "" : purpose) {
            case "credential" -> SignKeyPurpose.CREDENTIAL;
            case "status-list" -> SignKeyPurpose.STATUS_LIST;
            default -> throw new IllegalArgumentException("purpose must be credential or status-list");
        };
    }

    private SigningKeyOverviewResponse.Key key(SigningKeyOverview.KeyView view) {
        InstitutionSignKey master = view.master();
        return new SigningKeyOverviewResponse.Key(master.id(), purpose(master.purpose()), master.generation(),
            master.isCurrent(), master.mount(), master.keyName(), master.currentVersion(), master.status().name(),
            master.statusReason(), master.rotatedAt(), master.suspendedAt(), master.revokedAt(),
            master.compromisedAt(), view.versions().stream().map(this::version).toList());
    }

    private SigningKeyOverviewResponse.Version version(SigningKeyOverview.VersionView view) {
        var row = view.version();
        return new SigningKeyOverviewResponse.Version(row.keyVersion(), row.kidFragment(), view.kid(),
            row.status().name(), jwk(row.publicKeyJwk()), row.openbaoCreatedAt(), row.activatedAt(),
            row.retiredAt(), row.compromisedAt());
    }

    private Map<String, String> jwk(String storedJwk) {
        return objectMapper.readValue(storedJwk, JWK);
    }

    private static SigningKeyOverviewResponse.Operation operation(KmsOperation operation) {
        return new SigningKeyOverviewResponse.Operation(operation.id(), operation.signKeyId(),
            purpose(operation.purpose()), operation.action().name(), operation.status().name(),
            operation.targetKeyVersion(), operation.expectedVersionBefore(), operation.observedVersionAfter(),
            operation.preparedAt(), operation.lastCallAt(), operation.reason(), operation.requestedBy(),
            operation.approvalId(), operation.idempotencyKey(), operation.createdAt(), operation.updatedAt());
    }

    private static SigningKeyOverviewResponse.Approval approval(KmsApproval approval) {
        return new SigningKeyOverviewResponse.Approval(approval.id(), purpose(approval.purpose()),
            approval.action().name(), approval.signKeyId(), approval.expectedVersion(), approval.targetKeyVersion(),
            approval.targetCompromisedAt(), approval.approvedBy(), approval.expiresAt(), approval.createdAt());
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
