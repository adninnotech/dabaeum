package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialHashVersion;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile({"local", "dev"})
public class DefaultCredentialVerificationService implements CredentialVerificationService {

    private static final String PUBLIC_REQUESTER = "INDIVIDUAL";
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "verifiedAt,asc", "verifiedAt,desc");
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final CredentialRepository credentials;
    private final CredentialVerificationRepository verifications;
    private final CredentialProofService proofService;
    private final CredentialHashService hashService;
    private final BlockchainRegistryPort registry;
    private final CredentialApplicationService credentialApplicationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CredentialUriProvider uriProvider;
    private final CredentialStatusListRepository statusLists;
    private final RegistryTarget target;

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialProofService proofService,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider
    ) {
        this(credentials, verifications, proofService, hashService, registry,
            credentialApplicationService, objectMapper, clock, uriProvider, null);
    }

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialProofService proofService,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider,
        CredentialStatusListRepository statusLists
    ) {
        this(credentials, verifications, proofService, hashService, registry,
            credentialApplicationService, objectMapper, clock, uriProvider, statusLists,
            RegistryTarget.FABRIC_POC);
    }

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialProofService proofService,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider,
        CredentialStatusListRepository statusLists,
        RegistryTarget target
    ) {
        this.target = Objects.requireNonNull(target, "target");
        this.credentials = Objects.requireNonNull(credentials);
        this.verifications = Objects.requireNonNull(verifications);
        this.proofService = proofService;
        this.hashService = Objects.requireNonNull(hashService);
        this.registry = registry;
        this.credentialApplicationService = Objects.requireNonNull(credentialApplicationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.uriProvider = Objects.requireNonNull(uriProvider);
        this.statusLists = statusLists;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        ObjectProvider<CredentialProofService> proofService,
        ObjectProvider<CredentialUriProvider> uriProvider,
        CredentialHashService hashService,
        ObjectProvider<BlockchainRegistryPort> registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        ObjectProvider<CredentialStatusListRepository> statusLists,
        RegistryTarget target
    ) {
        this(credentials, verifications, proofService.getIfAvailable(), hashService,
            registry.getIfAvailable(),
            credentialApplicationService, objectMapper, clock, uriProvider.getIfAvailable(),
            statusLists.getIfAvailable(), target);
    }

    /**
     * VC 의 credentialStatus.id 가 이 Credential 의 것인지 본다. 전환 이전 발급분은 수료증 번호별
     * 상태 URL 을, 이후 발급분은 배정된 Bitstring Status List 칸을 가리킨다.
     */
    private boolean statusReferenceMatches(
        Credential credential,
        tools.jackson.databind.JsonNode credentialStatus
    ) {
        if (credentialStatus == null) {
            return true;
        }
        String statusId = credentialStatus.path("id").asString();
        if (uriProvider.statusUrl(credential.credentialNo()).equals(statusId)) {
            return true;
        }
        if (statusLists == null) {
            return false;
        }
        return statusLists.findEntryByCredentialId(credential.id())
            .map(entry -> (uriProvider.statusListUrl(entry.listId()) + "#" + entry.index())
                .equals(statusId))
            .orElse(false);
    }

    @Override
    public CredentialVerification verify(CredentialVerifyCommand command) {
        Objects.requireNonNull(command, "command");
        Instant verifiedAt = command.requestedAt() == null ? clock.instant() : command.requestedAt();
        Optional<Credential> credential = command.credentialNo() != null
            ? credentials.findByCredentialNo(command.credentialNo())
            : credentials.findByCredentialHash(command.credentialHash());
        CredentialVerificationResult result = credential
            .map(value -> evaluate(value, command, verifiedAt))
            .orElse(CredentialVerificationResult.NOT_FOUND);
        CredentialVerification verification = new CredentialVerification(
            UUID.randomUUID(), credential.map(Credential::id).orElse(null), command.credentialNo(),
            command.credentialHash(), command.verificationType(), PUBLIC_REQUESTER, null, result,
            verifiedAt, null, null, metadata(command.requestId()), verifiedAt);
        verifications.insert(verification);
        return verification;
    }

    @Override
    public CredentialVerificationPage list(
        UUID credentialId, int page, int size, String sort, AuthenticatedUserContext actor
    ) {
        if (credentialId == null) {
            throw validation("credentialId");
        }
        if (page < 0 || size < 1 || size > 100 || sort == null || sort.isBlank()
            || page > Integer.MAX_VALUE / size || !ALLOWED_SORTS.contains(sort.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size", "sort"));
        }
        credentialApplicationService.get(credentialId, actor);
        int offset = Math.multiplyExact(page, size);
        List<CredentialVerification> data = verifications.findByCredentialId(
            credentialId, size, offset, sort.trim());
        long total = verifications.countByCredentialId(credentialId);
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new CredentialVerificationPage(data, page, size, total, totalPages);
    }

    private CredentialVerificationResult evaluate(
        Credential credential, CredentialVerifyCommand command, Instant verifiedAt
    ) {
        if (proofService == null) {
            return CredentialVerificationResult.ERROR;
        }
        if (credential.vcPayload() == null || credential.vcHash() == null) {
            return CredentialVerificationResult.INVALID;
        }
        SignedCredentialEnvelope envelope;
        try {
            envelope = objectMapper.readValue(
                credential.vcPayload(), SignedCredentialEnvelope.class);
        } catch (Exception exception) {
            return CredentialVerificationResult.INVALID;
        }
        com.adn.dabaeum.credential.domain.CredentialDocument document;
        try {
            document = proofService.verify(envelope);
        } catch (CredentialProofService.ProofException exception) {
            return exception.failureCode() == CredentialProofService.FailureCode.CREDENTIAL_PROOF_INVALID
                ? CredentialVerificationResult.INVALID : CredentialVerificationResult.ERROR;
        } catch (RuntimeException exception) {
            return CredentialVerificationResult.ERROR;
        }
        try {
            String calculatedHash = isCurrentHashVersion(credential)
                ? hashService.sha256CompactJws(envelope.compactJws())
                : hashService.sha256Payload(credential.vcPayload());
            if (!calculatedHash.equals(credential.vcHash())) {
                return CredentialVerificationResult.INVALID;
            }
            tools.jackson.databind.JsonNode documentNode = objectMapper.readTree(document.canonicalJson());
            tools.jackson.databind.JsonNode validUntil = documentNode == null
                ? null : documentNode.get("validUntil");
            tools.jackson.databind.JsonNode credentialStatus = documentNode == null
                ? null : documentNode.get("credentialStatus");
            if (documentNode == null || !documentNode.isObject()
                || !("urn:uuid:" + credential.id()).equals(documentNode.path("id").asString())
                || !credential.issuerIdentifier().equals(documentNode.path("issuer").asString())
                || !credential.subjectIdentifier().equals(
                    documentNode.path("credentialSubject").path("id").asString())
                || !sameDatabaseInstant(credential.validFrom(), documentNode.path("validFrom").asString())
                || (credential.validUntil() == null ? validUntil != null
                    : validUntil == null || !sameDatabaseInstant(
                        credential.validUntil(), validUntil.asString()))
                || !statusReferenceMatches(credential, credentialStatus)) {
                return CredentialVerificationResult.INVALID;
            }
        } catch (Exception exception) {
            return CredentialVerificationResult.INVALID;
        }

        if (registry == null) {
            return CredentialVerificationResult.ERROR;
        }
        CredentialRegistryState ledger;
        try {
            ledger = registry.getCredentialState(reference(credential));
        } catch (BlockchainRegistryException exception) {
            return "BLOCKCHAIN_NOT_FOUND".equals(exception.code())
                ? CredentialVerificationResult.NOT_FOUND
                : CredentialVerificationResult.ERROR;
        } catch (RuntimeException exception) {
            return CredentialVerificationResult.ERROR;
        }
        if (!reference(credential).chainKey().equals(ledger.chainKey())
            || !credential.vcHash().equals(ledger.vcHash())) {
            return CredentialVerificationResult.INVALID;
        }
        if (credential.status() == CredentialStatus.REVOKED) {
            return ledger.status() == RegistryStatus.REVOKED
                ? CredentialVerificationResult.REVOKED
                : CredentialVerificationResult.INVALID;
        }
        if (credential.status() == CredentialStatus.SUPERSEDED) {
            boolean matchingSuperseded = credential.chainKey() == null
                ? ledger.status() == RegistryStatus.REVOKED
                : ledger.status() == RegistryStatus.SUPERSEDED;
            return matchingSuperseded
                ? CredentialVerificationResult.SUPERSEDED
                : CredentialVerificationResult.INVALID;
        }
        boolean activeLedger = ledger.status() == RegistryStatus.ACTIVE;
        if (credential.status() == CredentialStatus.EXPIRED
            || credential.validUntil() != null && !verifiedAt.isBefore(credential.validUntil())) {
            return activeLedger
                ? CredentialVerificationResult.EXPIRED
                : CredentialVerificationResult.INVALID;
        }
        if (credential.status() != CredentialStatus.ISSUED) {
            return CredentialVerificationResult.INVALID;
        }
        if (activeLedger) {
            return CredentialVerificationResult.VALID;
        }
        if (ledger.status() == RegistryStatus.REVOKED) {
            return CredentialVerificationResult.REVOKED;
        }
        if (ledger.status() == RegistryStatus.SUPERSEDED) {
            return CredentialVerificationResult.SUPERSEDED;
        }
        return CredentialVerificationResult.INVALID;
    }

    private boolean isCurrentHashVersion(Credential credential) {
        String version = credential.vcHashVersion();
        if (version == null || CredentialHashVersion.ENVELOPE_SHA256_V0.name().equals(version)) {
            return false;
        }
        return CredentialHashVersion.COMPACT_JWS_SHA256_V1.name().equals(version);
    }

    private CredentialRegistryReference reference(Credential credential) {
        return target.reference(credential.chainKey(), credential.credentialNo());
    }

    private String metadata(String requestId) {
        if (requestId == null || !UUID_PATTERN.matcher(requestId).matches()) {
            return "{}";
        }
        return "{\"requestId\":\"" + requestId + "\"}";
    }

    private boolean sameDatabaseInstant(Instant databaseValue, String payloadValue) {
        try {
            return Duration.between(databaseValue, Instant.parse(payloadValue)).abs()
                .compareTo(Duration.ofNanos(1_000)) <= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
