package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialHashVersion;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver.KeyResolutionException;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.VerificationKey;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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

    /** §9 6단계: 버전 행 키가 수료증 행에 기록된 키가 아니다. */
    static final String ISSUANCE_NOT_TRUSTED = "ISSUANCE_NOT_TRUSTED";
    /** §6.3: 유출 버전이고 원장 최초 기록이 유출 이전이 아니다. */
    static final String KEY_COMPROMISED = "KEY_COMPROMISED";
    /** §6.3 조건 3 을 판정할 원장 이력을 읽지 못했다. */
    static final String LEDGER_HISTORY_UNAVAILABLE = "LEDGER_HISTORY_UNAVAILABLE";
    /** §9 원장 단계: 발급 기록은 있는데 원장에 그 수료증 기록이 없다. */
    static final String LEDGER_RECORD_NOT_FOUND = "LEDGER_RECORD_NOT_FOUND";

    private static final String PUBLIC_REQUESTER = "INDIVIDUAL";
    private static final int HISTORY_PAGE = 10;
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "verifiedAt,asc", "verifiedAt,desc");
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final CredentialRepository credentials;
    private final CredentialVerificationRepository verifications;
    private final CredentialSignatureVerifier verifier;
    private final CredentialHashService hashService;
    private final BlockchainRegistryPort registry;
    private final CredentialApplicationService credentialApplicationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CredentialUriProvider uriProvider;
    private final CredentialStatusListRepository statusLists;
    private final RegistryTarget target;
    private final CredentialSigningRepository signing;
    private final InstitutionRepository institutions;

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialSignatureVerifier verifier,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider
    ) {
        this(credentials, verifications, verifier, hashService, registry,
            credentialApplicationService, objectMapper, clock, uriProvider, null);
    }

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialSignatureVerifier verifier,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider,
        CredentialStatusListRepository statusLists
    ) {
        this(credentials, verifications, verifier, hashService, registry,
            credentialApplicationService, objectMapper, clock, uriProvider, statusLists,
            RegistryTarget.FABRIC_POC);
    }

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialSignatureVerifier verifier,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider,
        CredentialStatusListRepository statusLists,
        RegistryTarget target
    ) {
        this(credentials, verifications, verifier, hashService, registry,
            credentialApplicationService, objectMapper, clock, uriProvider, statusLists, target, null);
    }

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialSignatureVerifier verifier,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider,
        CredentialStatusListRepository statusLists,
        RegistryTarget target,
        CredentialSigningRepository signing
    ) {
        this(credentials, verifications, verifier, hashService, registry,
            credentialApplicationService, objectMapper, clock, uriProvider, statusLists,
            target, signing, null);
    }

    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        CredentialSignatureVerifier verifier,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        CredentialUriProvider uriProvider,
        CredentialStatusListRepository statusLists,
        RegistryTarget target,
        CredentialSigningRepository signing,
        InstitutionRepository institutions
    ) {
        this.target = Objects.requireNonNull(target, "target");
        this.credentials = Objects.requireNonNull(credentials);
        this.verifications = Objects.requireNonNull(verifications);
        this.verifier = verifier;
        this.hashService = Objects.requireNonNull(hashService);
        this.registry = registry;
        this.credentialApplicationService = Objects.requireNonNull(credentialApplicationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.uriProvider = Objects.requireNonNull(uriProvider);
        this.statusLists = statusLists;
        this.signing = signing;
        this.institutions = institutions;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultCredentialVerificationService(
        CredentialRepository credentials,
        CredentialVerificationRepository verifications,
        ObjectProvider<CredentialSignatureVerifier> verifier,
        ObjectProvider<CredentialUriProvider> uriProvider,
        CredentialHashService hashService,
        ObjectProvider<BlockchainRegistryPort> registry,
        CredentialApplicationService credentialApplicationService,
        ObjectMapper objectMapper,
        Clock clock,
        ObjectProvider<CredentialStatusListRepository> statusLists,
        RegistryTarget target,
        ObjectProvider<CredentialSigningRepository> signing,
        ObjectProvider<InstitutionRepository> institutions
    ) {
        this(credentials, verifications, verifier.getIfAvailable(), hashService,
            registry.getIfAvailable(),
            credentialApplicationService, objectMapper, clock, uriProvider.getIfAvailable(),
            statusLists.getIfAvailable(), target, signing.getIfAvailable(), institutions.getIfAvailable());
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
        // 서명 당시 공개 주소로 비교한다. 전환 이전 발급분은 발급자가 urn 이라 상태 주소 자체로 판별한다.
        CredentialUriProvider uris = uriProvider.forSignedUrl(statusId);
        if (uris.statusUrl(credential.credentialNo()).equals(statusId)) {
            return true;
        }
        if (statusLists == null) {
            return false;
        }
        return statusLists.findEntryByCredentialId(credential.id())
            .map(entry -> (uris.statusListUrl(entry.listId()) + "#" + entry.index())
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
        Evaluation evaluation = credential
            .map(value -> evaluate(value, verifiedAt))
            .orElse(Evaluation.of(CredentialVerificationResult.NOT_FOUND));
        String issuerName = evaluation.institutionId() == null || institutions == null ? null
            : institutions.findById(evaluation.institutionId()).map(i -> i.name()).orElse(null);
        Instant issuedAt = issuerName == null ? null : evaluation.issuedAt();
        CredentialVerification verification = new CredentialVerification(
            UUID.randomUUID(), credential.map(Credential::id).orElse(null), command.credentialNo(),
            command.credentialHash(), command.verificationType(), PUBLIC_REQUESTER, null,
            evaluation.result(), verifiedAt, null, null, metadata(command.requestId(), evaluation),
            verifiedAt, issuerName, issuedAt);
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

    private Evaluation evaluate(Credential credential, Instant verifiedAt) {
        if (verifier == null) {
            return Evaluation.error(KeyResolutionException.UNAVAILABLE);
        }
        if (credential.vcPayload() == null || credential.vcHash() == null) {
            return Evaluation.of(CredentialVerificationResult.INVALID);
        }
        SignedCredentialEnvelope envelope;
        try {
            envelope = objectMapper.readValue(
                credential.vcPayload(), SignedCredentialEnvelope.class);
        } catch (Exception exception) {
            return Evaluation.of(CredentialVerificationResult.INVALID);
        }
        CredentialSignatureVerifier.VerifiedDocument verified;
        try {
            verified = verifier.verifyCredential(envelope);
        } catch (CredentialSignatureVerifier.VerificationFailure failure) {
            return failure.unavailable()
                ? Evaluation.error(failure.code()) : Evaluation.invalid(failure.code());
        } catch (RuntimeException exception) {
            return Evaluation.error(KeyResolutionException.UNAVAILABLE);
        }

        VerificationKey key = verified.key();
        Optional<Boolean> recordedKey = signedWithRecordedKey(credential, key);
        if (recordedKey.isEmpty()) {
            return Evaluation.error(KeyResolutionException.UNAVAILABLE);
        }
        if (!recordedKey.get()) {
            return Evaluation.invalid(ISSUANCE_NOT_TRUSTED);
        }
        boolean conditional = false;
        if (key.compromised()) {
            Optional<Boolean> anchored = anchoredBefore(credential, key.compromisedAt());
            if (anchored.isEmpty()) {
                return Evaluation.error(LEDGER_HISTORY_UNAVAILABLE);
            }
            if (!anchored.get()) {
                return Evaluation.invalid(KEY_COMPROMISED);
            }
            conditional = true;
        }

        CredentialDocument document = verified.document();
        Instant signedIssuedAt;
        try {
            String calculatedHash = isCurrentHashVersion(credential)
                ? hashService.sha256CompactJws(envelope.compactJws())
                : hashService.sha256Payload(credential.vcPayload());
            if (!calculatedHash.equals(credential.vcHash())) {
                return Evaluation.of(CredentialVerificationResult.INVALID);
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
                return Evaluation.of(CredentialVerificationResult.INVALID);
            }
            signedIssuedAt = Instant.parse(documentNode.path("validFrom").asString());
        } catch (Exception exception) {
            return Evaluation.of(CredentialVerificationResult.INVALID);
        }

        CredentialVerificationResult ledgerResult = ledgerResult(credential, verifiedAt);
        // 발급 기록은 있는데 원장에 없으면 NOT_FOUND(제시한 수료증이 없음)가 아니라 신뢰할 수 없는 발급이다.
        Evaluation evaluation = ledgerResult == CredentialVerificationResult.NOT_FOUND
            ? Evaluation.invalid(LEDGER_RECORD_NOT_FOUND) : Evaluation.of(ledgerResult);
        return evaluation.conditionally(conditional).fromDocument(
            verified.institutionId(), signedIssuedAt);
    }

    private CredentialVerificationResult ledgerResult(Credential credential, Instant verifiedAt) {
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

    /**
     * §9 6단계·§6.3 조건 2. 버전 행 키로 검증했으면 수료증 행의 sign_key_id·sign_key_version 이 그 키여야 한다.
     * 참조가 빈 행(파일 키로 발급)은 전환 이전 키로만 인정한다. 버전 행 없이 파일 키로 대신한 검증은 대조할
     * 참조가 없다.
     *
     * @return 판정. 서명 상태를 읽을 수 없으면 empty
     */
    private Optional<Boolean> signedWithRecordedKey(Credential credential, VerificationKey key) {
        if (key.signKeyId() == null) {
            return Optional.of(true);
        }
        if (signing == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(signing.findSigningState(credential.id())
                .map(state -> {
                    if (state.signKeyId() == null) {
                        return SignKeyNaming.LEGACY_KID_FRAGMENT.equals(key.kidFragment());
                    }
                    return state.signKeyId().equals(key.signKeyId())
                        && key.keyVersion().equals(state.signKeyVersion());
                })
                .orElse(false));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * §6.3 조건 3. 원장 이력의 가장 오래된 비삭제 기록이 유출 시각보다 앞서야 한다. 트랜잭션 행의 confirmed_at 은
     * 겹친 처리에서 덮어써질 수 있어 쓰지 않는다 (계획 4 설계서와 다른 점 2).
     *
     * @return 판정. 원장 이력을 읽을 수 없으면 empty
     */
    private Optional<Boolean> anchoredBefore(Credential credential, Instant compromisedAt) {
        if (registry == null) {
            return Optional.empty();
        }
        try {
            List<BlockchainHistoryEntry> history = registry.getCredentialHistory(
                reference(credential), new BlockchainHistoryQuery(HISTORY_PAGE, 0, false));
            return Optional.of(history.stream()
                .filter(entry -> !entry.deleted())
                .map(BlockchainHistoryEntry::timestamp)
                .min(Comparator.naturalOrder())
                .map(first -> first.isBefore(compromisedAt))
                .orElse(false));
        } catch (BlockchainRegistryException exception) {
            return "BLOCKCHAIN_NOT_FOUND".equals(exception.code())
                ? Optional.of(false) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
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

    /** 서버가 만든 값만 싣는다: 형식을 확인한 요청 ID, 고정 실패 코드, 조건부 표시. */
    private String metadata(String requestId, Evaluation evaluation) {
        StringBuilder json = new StringBuilder("{");
        if (requestId != null && UUID_PATTERN.matcher(requestId).matches()) {
            appendField(json, "requestId", requestId);
        }
        if (evaluation.failureCode() != null) {
            appendField(json, "failureCode", evaluation.failureCode());
        }
        if (evaluation.conditional()) {
            appendField(json, "verificationScope", CredentialVerification.PLATFORM_CONDITIONAL);
        }
        return json.append('}').toString();
    }

    private static void appendField(StringBuilder json, String name, String value) {
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(name).append("\":\"").append(value).append('"');
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
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }

    /** 검증 결과, 기록할 실패 코드, §6.3 조건부 인정 여부. */
    private record Evaluation(CredentialVerificationResult result, String failureCode, boolean conditional,
                              UUID institutionId, Instant issuedAt) {

        static Evaluation of(CredentialVerificationResult result) {
            return new Evaluation(result, null, false, null, null);
        }

        static Evaluation invalid(String failureCode) {
            return new Evaluation(CredentialVerificationResult.INVALID, failureCode, false, null, null);
        }

        static Evaluation error(String failureCode) {
            return new Evaluation(CredentialVerificationResult.ERROR, failureCode, false, null, null);
        }

        /** 판정이 난 결과(VALID·REVOKED·SUPERSEDED·EXPIRED)에만 조건부 표시를 붙인다. */
        Evaluation conditionally(boolean platformConditional) {
            boolean decided = result != CredentialVerificationResult.INVALID
                && result != CredentialVerificationResult.ERROR
                && result != CredentialVerificationResult.NOT_FOUND;
            return new Evaluation(result, failureCode, platformConditional && decided,
                institutionId, issuedAt);
        }

        Evaluation fromDocument(UUID id, Instant signedIssuedAt) {
            boolean decided = result != CredentialVerificationResult.INVALID
                && result != CredentialVerificationResult.ERROR
                && result != CredentialVerificationResult.NOT_FOUND;
            return new Evaluation(result, failureCode, conditional,
                decided ? id : null, decided ? signedIssuedAt : null);
        }
    }
}
