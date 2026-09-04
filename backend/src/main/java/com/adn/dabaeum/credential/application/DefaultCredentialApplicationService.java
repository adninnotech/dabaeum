package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.BlockchainRequestPort;
import com.adn.dabaeum.credential.application.port.ChainKeyGenerator;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialQueryRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialView;
import com.adn.dabaeum.credential.domain.CredentialHashVersion;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.PendingBlockchainRequest;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultCredentialApplicationService implements CredentialApplicationService {

    private static final String CREDENTIAL_TYPE = "LIFELONG_EDUCATION_COMPLETION";
    private static final String LEGACY_FABRIC_NETWORK = RegistryTarget.LEGACY_NETWORK;
    private static final String ANCHOR_TYPE = "VC_ANCHOR";
    private static final String REVOKE_TYPE = "VC_REVOKE";
    private static final String REISSUE_TYPE = "VC_REISSUE";
    private static final int MAX_CHAIN_KEY_ATTEMPTS = 5;

    private final CompletionRepository completionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CredentialGroupRepository groupRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialQueryRepository credentialQueryRepository;
    private final BlockchainRequestPort blockchainRequestPort;
    private final AuthorizationPolicy authorizationPolicy;
    private final CredentialGroupIdGenerator credentialGroupIdGenerator;
    private final CredentialIdGenerator credentialIdGenerator;
    private final CredentialNumberGenerator credentialNumberGenerator;
    private final ChainKeyGenerator chainKeyGenerator;
    private final Clock clock;
    private final RegistryTarget target;

    /** Fabric POC 대상. 기존 호출부와 테스트를 위해 남긴다. */
    public DefaultCredentialApplicationService(
        CompletionRepository completionRepository,
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        CredentialGroupRepository groupRepository,
        CredentialRepository credentialRepository,
        CredentialQueryRepository credentialQueryRepository,
        BlockchainRequestPort blockchainRequestPort,
        AuthorizationPolicy authorizationPolicy,
        CredentialGroupIdGenerator credentialGroupIdGenerator,
        CredentialIdGenerator credentialIdGenerator,
        CredentialNumberGenerator credentialNumberGenerator,
        ChainKeyGenerator chainKeyGenerator,
        Clock clock
    ) {
        this(completionRepository, enrollmentRepository, courseRepository, groupRepository,
            credentialRepository, credentialQueryRepository, blockchainRequestPort,
            authorizationPolicy, credentialGroupIdGenerator, credentialIdGenerator,
            credentialNumberGenerator, chainKeyGenerator, clock, RegistryTarget.FABRIC_POC);
    }

    @Autowired
    public DefaultCredentialApplicationService(
        CompletionRepository completionRepository,
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        CredentialGroupRepository groupRepository,
        CredentialRepository credentialRepository,
        CredentialQueryRepository credentialQueryRepository,
        BlockchainRequestPort blockchainRequestPort,
        AuthorizationPolicy authorizationPolicy,
        CredentialGroupIdGenerator credentialGroupIdGenerator,
        CredentialIdGenerator credentialIdGenerator,
        CredentialNumberGenerator credentialNumberGenerator,
        ChainKeyGenerator chainKeyGenerator,
        Clock clock,
        RegistryTarget target
    ) {
        this.target = Objects.requireNonNull(target, "target");
        this.completionRepository = Objects.requireNonNull(completionRepository);
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository);
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.groupRepository = Objects.requireNonNull(groupRepository);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.credentialQueryRepository = Objects.requireNonNull(credentialQueryRepository);
        this.blockchainRequestPort = Objects.requireNonNull(blockchainRequestPort);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.credentialGroupIdGenerator = Objects.requireNonNull(credentialGroupIdGenerator);
        this.credentialIdGenerator = Objects.requireNonNull(credentialIdGenerator);
        this.credentialNumberGenerator = Objects.requireNonNull(credentialNumberGenerator);
        this.chainKeyGenerator = Objects.requireNonNull(chainKeyGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public Credential issue(IssueCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        String requestHash = requestHash(command);
        Completion completion = completionRepository.findByIdForUpdate(command.completionId())
            .orElseThrow(() -> notFound(ApiErrorCode.COMPLETION_NOT_FOUND,
                "Completion not found"));
        if (completion.status() != CompletionStatus.COMPLETED) {
            throw conflict(ApiErrorCode.COMPLETION_NOT_CONFIRMED,
                "Completion is not confirmed");
        }
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(completion.enrollmentId())
            .orElseThrow(() -> notFound(ApiErrorCode.ENROLLMENT_NOT_FOUND,
                "Enrollment not found"));
        Course course = courseRepository.findActiveByIdForUpdate(enrollment.courseId())
            .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND, "Course not found"));
        authorizationPolicy.requireCourseManager(command.actor(), course.institutionId());

        // 멱등 결과를 재사용하기 전에 이수 범위 권한을 확인한다. 그렇지 않으면 기존 키를
        // 알아낸 호출자가 발급 인가 경계를 우회할 수 있다.
        Optional<PendingBlockchainRequest> existing =
            blockchainRequestPort.findByIdempotencyKey(
                target.network(), command.idempotencyKey());
        if (existing.isPresent()) {
            PendingBlockchainRequest request = existing.get();
            if (!requestHash.equals(request.requestHash())) {
                throw conflict(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT,
                    "Idempotency key was used for a different request");
            }
            return credentialRepository.findById(request.credentialId())
                .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND,
                    "Credential not found"));
        }

        CredentialGroup group = groupRepository.findByCompletionIdForUpdate(completion.id())
            .orElseGet(() -> createGroup(completion.id(), command.requestedAt()));
        if (credentialRepository.findActiveByGroupId(group.id()).isPresent()) {
            throw conflict(ApiErrorCode.CREDENTIAL_ALREADY_EXISTS,
                "An active Credential already exists");
        }

        UUID credentialId = Objects.requireNonNull(credentialIdGenerator.generate(),
            "generated credential id");
        Instant requestedAt = command.requestedAt();
        Credential pending;
        try {
            pending = insertPendingWithAvailableChainKey(
                credentialId, group.id(), null,
                credentialNumberGenerator.generate(credentialId),
                credentialRepository.nextVersionForUpdate(group.id()),
                command.validUntil(), requestedAt);
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Credential state changed concurrently");
        }
        try {
            blockchainRequestPort.createAnchor(new PendingBlockchainRequest(
                UUID.randomUUID(), credentialId, ANCHOR_TYPE, target.network(),
                command.idempotencyKey(), requestHash, requestedAt));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT,
                "Idempotency key was used concurrently");
        }
        return pending;
    }

    @Override
    @Transactional
    public Credential revoke(RevokeCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        Credential credential = credentialRepository.findByIdForUpdate(command.credentialId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential not found"));
        CredentialScope scope = scope(credential);
        authorizationPolicy.requireCourseManager(command.actor(), scope.institutionId());
        String requestHash = requestHash(command);
        Optional<PendingBlockchainRequest> existing = blockchainRequestPort
            .findByIdempotencyKey(
                networkFor(credential), command.idempotencyKey());
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().requestHash())) {
                throw conflict(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT,
                    "Idempotency key was used for a different request");
            }
            return credentialRepository.findById(existing.get().credentialId())
                .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential not found"));
        }
        CredentialGroup group = groupRepository.findByCompletionIdForUpdate(scope.completionId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential group not found"));
        Optional<Credential> active = credentialRepository.findActiveByGroupId(group.id());
        if (active.isPresent() && !active.get().id().equals(credential.id())) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "A credential operation is already in progress");
        }
        if (blockchainRequestPort.hasPendingOperationForGroup(group.id())) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "A credential operation is already in progress");
        }
        if (credential.status() != CredentialStatus.ISSUED) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Only an issued Credential can be revoked");
        }
        try {
            blockchainRequestPort.createAnchor(new PendingBlockchainRequest(
                UUID.randomUUID(), credential.id(), REVOKE_TYPE, networkFor(credential),
                command.idempotencyKey(), requestHash, command.requestedAt(), command.reason()));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT,
                "Idempotency key was used concurrently");
        }
        return credential;
    }

    @Override
    @Transactional
    public Credential reissue(ReissueCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        Credential previous = credentialRepository.findByIdForUpdate(command.credentialId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential not found"));
        CredentialScope scope = scope(previous);
        authorizationPolicy.requireCourseManager(command.actor(), scope.institutionId());
        String requestHash = requestHash(command);
        Optional<PendingBlockchainRequest> existing = blockchainRequestPort
            .findByIdempotencyKey(
                networkFor(previous), command.idempotencyKey());
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().requestHash())) {
                throw conflict(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT,
                    "Idempotency key was used for a different request");
            }
            return credentialRepository.findById(existing.get().credentialId())
                .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential not found"));
        }
        if (previous.status() != CredentialStatus.ISSUED) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Only an issued Credential can be reissued");
        }
        CredentialGroup group = groupRepository.findByCompletionIdForUpdate(scope.completionId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND, "Credential group not found"));
        Optional<Credential> active = credentialRepository.findActiveByGroupId(group.id());
        if (active.isPresent() && !active.get().id().equals(previous.id())) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "A credential operation is already in progress");
        }
        if (blockchainRequestPort.hasPendingOperationForGroup(group.id())) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "A credential operation is already in progress");
        }
        UUID newId = Objects.requireNonNull(credentialIdGenerator.generate(), "generated credential id");
        Instant requestedAt = command.requestedAt();
        Credential pending;
        try {
            pending = previous.chainKey() == null
                ? insertLegacyPending(
                    newId, group.id(), previous.id(), credentialNumberGenerator.generate(newId),
                    credentialRepository.nextVersionForUpdate(group.id()), command.validUntil(),
                    requestedAt)
                : insertPendingWithAvailableChainKey(
                    newId, group.id(), previous.id(), credentialNumberGenerator.generate(newId),
                    credentialRepository.nextVersionForUpdate(group.id()), command.validUntil(),
                    requestedAt);
            blockchainRequestPort.createAnchor(new PendingBlockchainRequest(
                UUID.randomUUID(), newId, REISSUE_TYPE, networkFor(previous),
                command.idempotencyKey(), requestHash, requestedAt, command.reason()));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Credential state changed concurrently");
        }
        return pending;
    }

    private Credential insertPendingWithAvailableChainKey(
        UUID credentialId,
        UUID groupId,
        UUID previousCredentialId,
        String credentialNo,
        int versionNo,
        Instant validUntil,
        Instant requestedAt
    ) {
        for (int attempt = 0; attempt < MAX_CHAIN_KEY_ATTEMPTS; attempt++) {
            Credential pending = new Credential(
                credentialId, groupId, previousCredentialId, credentialNo, versionNo,
                null, null, CREDENTIAL_TYPE, CredentialStatus.PENDING, null, validUntil,
                null, null, chainKeyGenerator.generate(),
                CredentialHashVersion.COMPACT_JWS_SHA256_V1.name(), null, null, null,
                null, null, requestedAt, requestedAt);
            if (credentialRepository.insertIfChainKeyAvailable(pending)) {
                return pending;
            }
        }
        throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
            "Could not allocate a unique Credential chain key");
    }

    private Credential insertLegacyPending(
        UUID credentialId,
        UUID groupId,
        UUID previousCredentialId,
        String credentialNo,
        int versionNo,
        Instant validUntil,
        Instant requestedAt
    ) {
        Credential pending = new Credential(
            credentialId, groupId, previousCredentialId, credentialNo, versionNo,
            null, null, CREDENTIAL_TYPE, CredentialStatus.PENDING, null, validUntil,
            null, null, null, CredentialHashVersion.ENVELOPE_SHA256_V0.name(),
            null, null, null, null, null, requestedAt, requestedAt);
        credentialRepository.insert(pending);
        return pending;
    }

    private String networkFor(Credential credential) {
        return credential.chainKey() == null
            ? LEGACY_FABRIC_NETWORK : target.network();
    }

    @Override
    @Transactional(readOnly = true)
    public CredentialView get(UUID credentialId, AuthenticatedUserContext actor) {
        if (credentialId == null) {
            throw validation("credentialId");
        }
        Credential credential = credentialRepository.findById(credentialId)
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND,
                "Credential not found"));
        CredentialScope scope = scope(credential);
        authorizationPolicy.requireCredentialSubjectOrInstitutionReader(
            actor, scope.userId(), scope.institutionId());
        return new CredentialView(
            credential,
            credentialQueryRepository.findCourseByCredentialId(credentialId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public CredentialPage listByUser(ListUserCredentialsQuery query) {
        Objects.requireNonNull(query, "query");
        int offset = Math.multiplyExact(query.page(), query.size());
        List<Credential> credentials;
        long totalElements;
        if (Objects.equals(query.actor().userId(), query.userId()) || isPlatformAdmin(query.actor())) {
            credentials = credentialRepository.findByUserId(
                query.userId(), query.size(), offset, query.sort());
            totalElements = credentialRepository.countByUserId(query.userId());
        } else {
            // 기관 관리자는 자기 기관 과정의 수료증만 본다. 학습자가 여러 기관 과정을 들었더라도
            // 타 기관 건 때문에 전체가 거부되지 않고, 권한 있는 건만 결과에 남는다.
            List<UUID> institutionIds = adminInstitutionIds(query.actor());
            authorizationPolicy.requireCredentialListReader(query.actor(), institutionIds);
            credentials = credentialQueryRepository.findByUserIdAndInstitutionIds(
                query.userId(), institutionIds, query.size(), offset, query.sort());
            totalElements = credentialQueryRepository.countByUserIdAndInstitutionIds(
                query.userId(), institutionIds);
        }
        int totalPages = totalElements == 0
            ? 0 : (int) ((totalElements + query.size() - 1) / query.size());
        return new CredentialPage(
            toViews(credentials), query.page(), query.size(), totalElements, totalPages);
    }

    private static boolean isPlatformAdmin(AuthenticatedUserContext actor) {
        return actor.roles().stream().anyMatch(role ->
            "PLATFORM_ADMIN".equals(role.role()) && role.institutionId() == null);
    }

    private static List<UUID> adminInstitutionIds(AuthenticatedUserContext actor) {
        return actor.roles().stream()
            .filter(role -> "INSTITUTION_ADMIN".equals(role.role()) && role.institutionId() != null)
            .map(role -> role.institutionId())
            .distinct()
            .toList();
    }

    // 목록의 Credential 전체에 대한 과정 정보를 한 번의 조회로 채운다.
    private List<CredentialView> toViews(List<Credential> credentials) {
        if (credentials.isEmpty()) {
            return List.of();
        }
        Map<UUID, CredentialCourseView> courses = credentialQueryRepository
            .findCoursesByCredentialIds(credentials.stream().map(Credential::id).toList())
            .stream()
            .collect(Collectors.toMap(
                CredentialCourseView::credentialId, Function.identity(), (first, second) -> first));
        return credentials.stream()
            .map(credential -> new CredentialView(credential, courses.get(credential.id())))
            .toList();
    }

    private CredentialGroup createGroup(UUID completionId, Instant createdAt) {
        CredentialGroup group = new CredentialGroup(
            credentialGroupIdGenerator.generate(), completionId, createdAt);
        try {
            groupRepository.insert(group);
            return group;
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Credential group changed concurrently");
        }
    }

    private CredentialScope scope(Credential credential) {
        CredentialGroup group = groupRepository.findById(credential.credentialGroupId())
            .orElseThrow(() -> notFound(ApiErrorCode.CREDENTIAL_NOT_FOUND,
                "Credential not found"));
        Completion completion = completionRepository.findById(group.completionId())
            .orElseThrow(() -> notFound(ApiErrorCode.COMPLETION_NOT_FOUND,
                "Completion not found"));
        Enrollment enrollment = enrollmentRepository.findById(completion.enrollmentId())
            .orElseThrow(() -> notFound(ApiErrorCode.ENROLLMENT_NOT_FOUND,
                "Enrollment not found"));
        Course course = courseRepository.findById(enrollment.courseId())
            .orElseThrow(() -> notFound(ApiErrorCode.COURSE_NOT_FOUND, "Course not found"));
        return new CredentialScope(enrollment.userId(), course.institutionId(), completion.id());
    }

    private String requestHash(IssueCredentialCommand command) {
        String canonicalRequest = command.completionId() + "|"
            + (command.validUntil() == null ? "null" : command.validUntil().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String requestHash(RevokeCredentialCommand command) {
        return sha256Hex(command.credentialId() + "|VC_REVOKE|" + command.reason());
    }

    private String requestHash(ReissueCredentialCommand command) {
        return sha256Hex(command.credentialId() + "|VC_REISSUE|" + command.reason() + "|"
            + (command.validUntil() == null ? "null" : command.validUntil().toString()));
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid", List.of(field));
    }

    private ApiException notFound(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record CredentialScope(UUID userId, UUID institutionId, UUID completionId) {
    }
}
