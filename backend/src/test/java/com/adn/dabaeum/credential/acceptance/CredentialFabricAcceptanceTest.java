package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.blockchain.config.BlockchainProviderConfiguration;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.LegacyCredentialIssueDetails;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialVerifyCommand;
import com.adn.dabaeum.credential.application.DefaultCredentialApplicationService;
import com.adn.dabaeum.credential.application.DefaultCredentialUriProvider;
import com.adn.dabaeum.credential.application.DefaultCredentialVerificationService;
import com.adn.dabaeum.credential.application.IssueCredentialCommand;
import com.adn.dabaeum.credential.application.RevokeCredentialCommand;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.BlockchainRequestPort;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.PendingBlockchainRequest;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialQueryRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.fabric.application.CredentialFabricIssuanceContext;
import com.adn.dabaeum.fabric.application.DefaultCredentialFabricWorker;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class CredentialFabricAcceptanceTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final Instant ISSUED_AT = Instant.parse("2026-08-13T01:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-08-13T02:00:00Z");
    private static final Set<String> CREDENTIAL_OPERATION_IDS = Set.of(
        "issueCredential", "getCredential", "listUserCredentials", "revokeCredential",
        "reissueCredential", "verifyCredential", "listCredentialVerifications",
        "downloadCredentialDocument", "listCurrentUserCredentials");

    @Test
    void acceptsNineCredentialOperationsAndAllVerificationResults() {
        OpenAPI api = parse();
        Map<String, Operation> operations = api.getPaths().values().stream()
            .flatMap(path -> path.readOperations().stream())
            .filter(operation -> operation.getTags() != null
                && operation.getTags().contains("Credential"))
            .collect(java.util.stream.Collectors.toMap(Operation::getOperationId, value -> value));

        assertThat(operations.keySet()).containsExactlyInAnyOrderElementsOf(CREDENTIAL_OPERATION_IDS);
        assertThat(CredentialVerificationResult.values())
            .extracting(Enum::name)
            .containsExactly("VALID", "INVALID", "REVOKED", "SUPERSEDED", "EXPIRED",
                "NOT_FOUND", "ERROR");
    }

    @Test
    void acceptanceArtifactsArePresent() throws Exception {
        assertThat(Files.exists(Path.of("scripts/verify-credential-vc-fabric.sh"))).isTrue();
        assertThat(Files.exists(Path.of("src/docs/asciidoc/credential-vc-fabric.adoc"))).isTrue();
    }

    @Test
    void fake_provider_accepts_issue_register_read_verify_revoke_and_reverify_flow() {
        new ApplicationContextRunner()
            .withUserConfiguration(BlockchainProviderConfiguration.class)
            .withPropertyValues("dabaeum.blockchain.provider=fake")
            .run(context -> {
                BlockchainRegistryPort registry = context.getBean(BlockchainRegistryPort.class);
                WorkflowFixture fixture = new WorkflowFixture(registry);

                Credential pending = fixture.issue();
                assertThat(pending.status()).isEqualTo(CredentialStatus.PENDING);
                assertThat(fixture.process(ISSUED_AT)).isEqualTo(1);
                assertThat(fixture.credential().status()).isEqualTo(CredentialStatus.ISSUED);
                assertThat(registry.getCredentialState(fixture.reference()).status())
                    .isEqualTo(RegistryStatus.ACTIVE);
                assertThat(fixture.verify()).isEqualTo(CredentialVerificationResult.VALID);

                assertThat(fixture.requestRevoke().status()).isEqualTo(CredentialStatus.ISSUED);
                assertThat(fixture.process(REVOKED_AT)).isEqualTo(1);

                assertThat(fixture.credential().status()).isEqualTo(CredentialStatus.REVOKED);
                assertThat(registry.getCredentialState(fixture.reference()).status())
                    .isEqualTo(RegistryStatus.REVOKED);
                assertThat(fixture.verify()).isEqualTo(CredentialVerificationResult.REVOKED);
                assertThat(fixture.verifications).hasSize(2);
            });
    }

    @Test
    void fake_provider_keeps_legacy_envelope_hash_fixture_verifiable() {
        new ApplicationContextRunner()
            .withUserConfiguration(BlockchainProviderConfiguration.class)
            .withPropertyValues("dabaeum.blockchain.provider=fake")
            .run(context -> {
                BlockchainRegistryPort registry = context.getBean(BlockchainRegistryPort.class);
                AcceptanceFixture fixture = AcceptanceFixture.legacy(registry);

                registry.createCredentialState(fixture.createCommand());

                assertThat(fixture.credential().vcHashVersion())
                    .isEqualTo("ENVELOPE_SHA256_V0");
                assertThat(fixture.verify()).isEqualTo(CredentialVerificationResult.VALID);
            });
    }

    private static OpenAPI parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        return result.getOpenAPI();
    }

    private static final class NoCourseCredentialQueryRepository
        implements CredentialQueryRepository {

        @Override
        public Optional<CredentialCourseView> findCourseByCredentialId(UUID credentialId) {
            return Optional.empty();
        }

        @Override
        public List<CredentialCourseView> findCoursesByCredentialIds(List<UUID> credentialIds) {
            return List.of();
        }

        @Override
        public List<Credential> findByUserIdAndInstitutionIds(
            UUID userId, List<UUID> institutionIds, int limit, int offset, String sort
        ) {
            return List.of();
        }

        @Override
        public long countByUserIdAndInstitutionIds(UUID userId, List<UUID> institutionIds) {
            return 0L;
        }
    }

    private static final class AcceptanceFixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final InMemoryCredentialRepository credentials = new InMemoryCredentialRepository();
        private final List<CredentialVerification> verifications = new ArrayList<>();
        private final BlockchainRegistryPort registry;
        private final CredentialProofService proof;
        private final DefaultCredentialVerificationService verificationService;
        private Credential credential;

        private AcceptanceFixture(BlockchainRegistryPort registry, boolean legacy) {
            this.registry = registry;
            SignedCredentialEnvelope envelope = new SignedCredentialEnvelope(
                "application/vc+jwt", "header.payload.signature");
            CredentialHashService hashes = new CredentialHashService(objectMapper);
            String payload = hashes.serialize(envelope);
            String hash = legacy
                ? hashes.sha256Payload(payload)
                : hashes.sha256CompactJws(envelope.compactJws());
            String credentialNo = legacy ? "CERT-LEGACY-001" : "CERT-ACCEPTANCE-001";
            String chainKey = legacy ? null : "ACCEPTANCEKEY001";
            String hashVersion = legacy
                ? "ENVELOPE_SHA256_V0" : "COMPACT_JWS_SHA256_V1";
            Credential pending = new Credential(
                UUID.randomUUID(), UUID.randomUUID(), null, credentialNo, 1,
                null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
                null, ISSUED_AT.plusSeconds(86400), null, null, chainKey, hashVersion,
                null, null, null, null, null, ISSUED_AT.minusSeconds(1),
                ISSUED_AT.minusSeconds(1));
            credential = pending.startIssuing(ISSUED_AT).markIssued(
                payload, hash,
                "urn:dabaeum:institution:00000000-0000-0000-0000-000000000001",
                "urn:dabaeum:user:00000000-0000-0000-0000-000000000002",
                ISSUED_AT, ISSUED_AT);
            credentials.insert(credential);
            CredentialDocument document = new CredentialDocument(
                "{\"id\":\"urn:uuid:" + credential.id()
                    + "\",\"issuer\":\"" + credential.issuerIdentifier()
                    + "\",\"validFrom\":\"" + credential.validFrom()
                    + "\",\"validUntil\":\"" + credential.validUntil()
                    + "\",\"credentialSubject\":{\"id\":\""
                    + credential.subjectIdentifier() + "\"}}");
            proof = new StaticCredentialProofService(envelope, document);
            CredentialVerificationRepository verificationRepository =
                new RecordingVerificationRepository(verifications);
            verificationService = new DefaultCredentialVerificationService(
                credentials, verificationRepository, proof, hashes, registry,
                mock(CredentialApplicationService.class), objectMapper,
                Clock.fixed(REVOKED_AT.plusSeconds(1), ZoneOffset.UTC),
                new DefaultCredentialUriProvider(new VcProperties(
                    "https://vc.example.test", "v1", "acceptance-1")));
        }

        private static AcceptanceFixture current(BlockchainRegistryPort registry) {
            return new AcceptanceFixture(registry, false);
        }

        private static AcceptanceFixture legacy(BlockchainRegistryPort registry) {
            return new AcceptanceFixture(registry, true);
        }

        private CredentialRegistryReference reference() {
            return credential.chainKey() == null
                ? new CredentialRegistryReference(
                    BlockchainProvider.FABRIC_POC, null, credential.credentialNo())
                : new CredentialRegistryReference(
                    BlockchainProvider.FABRIC_POC, credential.chainKey(), null);
        }

        private CredentialRegistryCreate createCommand() {
            LegacyCredentialIssueDetails details = reference().isLegacy()
                ? new LegacyCredentialIssueDetails(
                    "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
                    credential.credentialType(), ISSUED_AT)
                : null;
            return new CredentialRegistryCreate(
                reference(), 1, RegistryStatus.ACTIVE, credential.vcHash(),
                credential.vcHashVersion(), ISSUED_AT, details);
        }

        private CredentialVerificationResult verify() {
            return verificationService.verify(new CredentialVerifyCommand(
                credential.credentialNo(), null, "API", "INDIVIDUAL", null,
                REVOKED_AT.plusSeconds(1))).result();
        }

        private void revokeCredential() {
            credential = credential.markRevoked("acceptance revoke", REVOKED_AT);
            credentials.update(credential);
        }

        private Credential credential() {
            return credential;
        }
    }

    private static final class WorkflowFixture {
        private static final UUID COMPLETION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
        private static final UUID ENROLLMENT_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
        private static final UUID COURSE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000003");
        private static final UUID INSTITUTION_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000004");
        private static final UUID USER_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000005");
        private static final UUID GROUP_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000006");
        private static final UUID CREDENTIAL_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000007");

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final InMemoryCredentialRepository credentials = new InMemoryCredentialRepository();
        private final InMemoryCredentialGroupRepository groups =
            new InMemoryCredentialGroupRepository();
        private final InMemoryBlockchainTransactions transactions =
            new InMemoryBlockchainTransactions();
        private final InMemoryBlockchainRequestPort blockchain =
            new InMemoryBlockchainRequestPort(transactions);
        private final List<CredentialVerification> verifications = new ArrayList<>();
        private final BlockchainRegistryPort registry;
        private final DefaultCredentialApplicationService application;
        private final DefaultCredentialFabricWorker worker;
        private final DefaultCredentialVerificationService verificationService;

        private WorkflowFixture(BlockchainRegistryPort registry) {
            this.registry = registry;
            CompletionRepository completionRepository = mock(CompletionRepository.class);
            EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
            CourseRepository courseRepository = mock(CourseRepository.class);
            Completion completion = completedCompletion();
            Enrollment enrollment = approvedEnrollment();
            Course course = completedCourse();
            when(completionRepository.findByIdForUpdate(COMPLETION_ID))
                .thenReturn(Optional.of(completion));
            when(completionRepository.findById(COMPLETION_ID))
                .thenReturn(Optional.of(completion));
            when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
                .thenReturn(Optional.of(enrollment));
            when(enrollmentRepository.findById(ENROLLMENT_ID))
                .thenReturn(Optional.of(enrollment));
            when(courseRepository.findActiveByIdForUpdate(COURSE_ID))
                .thenReturn(Optional.of(course));
            when(courseRepository.findActiveById(COURSE_ID))
                .thenReturn(Optional.of(course));
            when(courseRepository.findById(COURSE_ID))
                .thenReturn(Optional.of(course));

            Clock clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
            application = new DefaultCredentialApplicationService(
                completionRepository, enrollmentRepository, courseRepository, groups,
                credentials, new NoCourseCredentialQueryRepository(), blockchain,
                new AuthorizationPolicy(), () -> GROUP_ID,
                () -> CREDENTIAL_ID, ignored -> "CERT-ACCEPTANCE-001",
                () -> "ACCEPTANCEKEY001", clock);
            WorkflowProofService proof = new WorkflowProofService();
            CredentialHashService hashes = new CredentialHashService(objectMapper);
            worker = new DefaultCredentialFabricWorker(
                transactions, credentials,
                (credentialId, issuedAt) -> new CredentialFabricIssuanceContext(
                    credentialDocument(credentialId, issuedAt),
                    "urn:dabaeum:institution:" + INSTITUTION_ID,
                    "urn:dabaeum:user:" + USER_ID,
                    issuedAt),
                proof, hashes, registry, new NoOpTransactionManager());
            verificationService = new DefaultCredentialVerificationService(
                credentials, new RecordingVerificationRepository(verifications), proof,
                hashes, registry, application, objectMapper,
                Clock.fixed(REVOKED_AT.plusSeconds(1), ZoneOffset.UTC),
                new DefaultCredentialUriProvider(new VcProperties(
                    "https://vc.example.test", "v1", "acceptance-1")));
        }

        private Credential issue() {
            return application.issue(new IssueCredentialCommand(
                COMPLETION_ID, ISSUED_AT.plusSeconds(86400), "acceptance-issue-001",
                actor(), ISSUED_AT.minusSeconds(1)));
        }

        private Credential requestRevoke() {
            return application.revoke(new RevokeCredentialCommand(
                CREDENTIAL_ID, "acceptance revoke", "acceptance-revoke-001", actor(),
                REVOKED_AT));
        }

        private int process(Instant now) {
            return worker.processBatch(10, now);
        }

        private Credential credential() {
            return credentials.findById(CREDENTIAL_ID).orElseThrow();
        }

        private CredentialRegistryReference reference() {
            return new CredentialRegistryReference(
                BlockchainProvider.FABRIC_POC, credential().chainKey(), null);
        }

        private CredentialVerificationResult verify() {
            return verificationService.verify(new CredentialVerifyCommand(
                credential().credentialNo(), null, "API", "INDIVIDUAL", null,
                REVOKED_AT.plusSeconds(1))).result();
        }

        private AuthenticatedUserContext actor() {
            return new AuthenticatedUserContext(
                USER_ID, "LOCAL",
                Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
        }

        private CredentialDocument credentialDocument(UUID credentialId, Instant validFrom) {
            return new CredentialDocument(
                "{\"id\":\"urn:uuid:" + credentialId
                    + "\",\"issuer\":\"urn:dabaeum:institution:" + INSTITUTION_ID
                    + "\",\"validFrom\":\"" + validFrom
                    + "\",\"validUntil\":\"" + ISSUED_AT.plusSeconds(86400)
                    + "\",\"credentialSubject\":{\"id\":\"urn:dabaeum:user:"
                    + USER_ID + "\"}}");
        }

        private static Completion completedCompletion() {
            return new Completion(
                COMPLETION_ID, ENROLLMENT_ID, CompletionStatus.COMPLETED,
                new BigDecimal("90.00"), 1200, new BigDecimal("3.00"),
                ISSUED_AT.minusSeconds(60), ISSUED_AT.minusSeconds(60), USER_ID,
                ISSUED_AT.minusSeconds(60), null, ISSUED_AT.minusSeconds(120),
                ISSUED_AT.minusSeconds(60));
        }

        private static Enrollment approvedEnrollment() {
            return new Enrollment(
                ENROLLMENT_ID, COURSE_ID, USER_ID, USER_ID,
                EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED,
                ISSUED_AT.minusSeconds(3600), ISSUED_AT.minusSeconds(1800),
                null, null, null, null, null, ISSUED_AT.minusSeconds(3600),
                ISSUED_AT.minusSeconds(1800));
        }

        private static Course completedCourse() {
            return new Course(
                COURSE_ID, INSTITUTION_ID, "COURSE-ACCEPTANCE", "인수 테스트 과정",
                null, "평생교육", CourseEducationType.ONLINE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, null, 30, null, null, true, new BigDecimal("3.00"),
                CourseStatus.COMPLETED, ISSUED_AT.minusSeconds(3600),
                ISSUED_AT.minusSeconds(60), null);
        }
    }

    private static final class WorkflowProofService implements CredentialProofService {
        private static final SignedCredentialEnvelope ENVELOPE = new SignedCredentialEnvelope(
            "application/vc+jwt", "header.payload.signature");
        private CredentialDocument signedDocument;

        @Override
        public SignedCredentialEnvelope sign(CredentialDocument document) {
            signedDocument = document;
            return ENVELOPE;
        }

        @Override
        public CredentialDocument verify(SignedCredentialEnvelope envelope) {
            if (!ENVELOPE.equals(envelope) || signedDocument == null) {
                throw new ProofException(FailureCode.CREDENTIAL_PROOF_INVALID, "invalid proof");
            }
            return signedDocument;
        }
    }

    private static final class InMemoryCredentialGroupRepository
        implements CredentialGroupRepository {
        private final Map<UUID, CredentialGroup> byCompletion = new java.util.LinkedHashMap<>();

        @Override
        public Optional<CredentialGroup> findById(UUID groupId) {
            return byCompletion.values().stream()
                .filter(value -> value.id().equals(groupId))
                .findFirst();
        }

        @Override
        public Optional<CredentialGroup> findByCompletionId(UUID completionId) {
            return Optional.ofNullable(byCompletion.get(completionId));
        }

        @Override
        public Optional<CredentialGroup> findByCompletionIdForUpdate(UUID completionId) {
            return findByCompletionId(completionId);
        }

        @Override
        public void insert(CredentialGroup group) {
            byCompletion.put(group.completionId(), group);
        }
    }

    private static final class InMemoryBlockchainRequestPort implements BlockchainRequestPort {
        private final Map<UUID, PendingBlockchainRequest> requests =
            new java.util.LinkedHashMap<>();
        private final InMemoryBlockchainTransactions transactions;

        private InMemoryBlockchainRequestPort(InMemoryBlockchainTransactions transactions) {
            this.transactions = transactions;
        }

        @Override
        public Optional<PendingBlockchainRequest> findByIdempotencyKey(
            String network,
            String idempotencyKey
        ) {
            return requests.values().stream()
                .filter(value -> value.network().equals(network))
                .filter(value -> value.idempotencyKey().equals(idempotencyKey))
                .findFirst();
        }

        @Override
        public void createAnchor(PendingBlockchainRequest request) {
            requests.put(request.transactionId(), request);
            transactions.insert(new BlockchainTransaction(
                request.transactionId(), "CREDENTIAL", request.credentialId(), request.network(),
                BlockchainTransactionType.valueOf(request.transactionType()),
                request.idempotencyKey(), request.transactionId(), request.requestHash(), null,
                BlockchainTransactionStatus.PENDING, null, null, request.requestedAt(), null,
                0, null, null, request.requestedAt(), request.requestedAt(),
                request.operationReason()));
        }

        @Override
        public boolean hasPendingOperationForGroup(UUID credentialGroupId) {
            return transactions.hasPending();
        }
    }

    private static final class InMemoryBlockchainTransactions
        implements BlockchainTransactionRepository {
        private final Map<UUID, BlockchainTransaction> transactions =
            new java.util.LinkedHashMap<>();

        private boolean hasPending() {
            return transactions.values().stream().anyMatch(value ->
                value.status() == BlockchainTransactionStatus.PENDING
                    || value.status() == BlockchainTransactionStatus.PROCESSING);
        }

        @Override
        public List<BlockchainTransaction> claimDue(
            List<String> networks,
            int limit,
            Instant now
        ) {
            List<BlockchainTransaction> claimed = transactions.values().stream()
                .filter(value -> networks.contains(value.network()))
                .filter(value -> value.status() == BlockchainTransactionStatus.PENDING)
                .filter(value -> value.nextRetryAt() == null
                    || !now.isBefore(value.nextRetryAt()))
                .limit(limit)
                .map(value -> value.claim(now))
                .toList();
            claimed.forEach(value -> transactions.put(value.id(), value));
            return claimed;
        }

        @Override
        public List<BlockchainTransaction> claimStale(
            List<String> networks,
            int limit,
            Instant now,
            Instant staleBefore
        ) {
            return List.of();
        }

        @Override
        public Optional<BlockchainTransaction> findById(UUID transactionId) {
            return Optional.ofNullable(transactions.get(transactionId));
        }

        @Override
        public Optional<BlockchainTransaction> findByIdempotencyKey(
            String network,
            String key
        ) {
            return transactions.values().stream()
                .filter(value -> value.network().equals(network))
                .filter(value -> value.idempotencyKey().equals(key))
                .findFirst();
        }

        @Override
        public void insert(BlockchainTransaction transaction) {
            transactions.put(transaction.id(), transaction);
        }

        @Override
        public void update(BlockchainTransaction transaction) {
            transactions.put(transaction.id(), transaction);
        }
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new DefaultTransactionStatus(
                "acceptance", null, true, true, false, false, false, null);
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    private static final class StaticCredentialProofService implements CredentialProofService {
        private final SignedCredentialEnvelope expected;
        private final CredentialDocument document;

        private StaticCredentialProofService(
            SignedCredentialEnvelope expected,
            CredentialDocument document
        ) {
            this.expected = expected;
            this.document = document;
        }

        @Override
        public SignedCredentialEnvelope sign(CredentialDocument ignored) {
            return expected;
        }

        @Override
        public CredentialDocument verify(SignedCredentialEnvelope envelope) {
            if (!expected.equals(envelope)) {
                throw new ProofException(FailureCode.CREDENTIAL_PROOF_INVALID, "invalid proof");
            }
            return document;
        }
    }

    private static final class InMemoryCredentialRepository implements CredentialRepository {
        private final Map<UUID, Credential> values = new java.util.LinkedHashMap<>();

        @Override
        public Optional<Credential> findById(UUID credentialId) {
            return Optional.ofNullable(values.get(credentialId));
        }

        @Override
        public Optional<Credential> findByCredentialNo(String credentialNo) {
            return values.values().stream()
                .filter(value -> value.credentialNo().equals(credentialNo))
                .findFirst();
        }

        @Override
        public Optional<Credential> findByCredentialHash(String credentialHash) {
            return values.values().stream()
                .filter(value -> credentialHash.equals(value.vcHash()))
                .findFirst();
        }

        @Override
        public Optional<Credential> findByIdForUpdate(UUID credentialId) {
            return findById(credentialId);
        }

        @Override
        public Optional<Credential> findActiveByGroupId(UUID groupId) {
            return values.values().stream()
                .filter(value -> value.credentialGroupId().equals(groupId))
                .filter(value -> value.status() == CredentialStatus.ISSUED)
                .findFirst();
        }

        @Override
        public List<Credential> findByUserId(UUID userId, int limit, int offset, String sort) {
            return List.of();
        }

        @Override
        public long countByUserId(UUID userId) {
            return 0;
        }

        @Override
        public int nextVersionForUpdate(UUID groupId) {
            return 1;
        }

        @Override
        public void insert(Credential value) {
            values.put(value.id(), value);
        }

        @Override
        public boolean insertIfChainKeyAvailable(Credential value) {
            insert(value);
            return true;
        }

        @Override
        public void update(Credential value) {
            values.put(value.id(), value);
        }
    }

    private static final class RecordingVerificationRepository
        implements CredentialVerificationRepository {
        private final List<CredentialVerification> values;

        private RecordingVerificationRepository(List<CredentialVerification> values) {
            this.values = values;
        }

        @Override
        public void insert(CredentialVerification verification) {
            values.add(verification);
        }

        @Override
        public List<CredentialVerification> findByCredentialId(
            UUID credentialId, int limit, int offset, String sort
        ) {
            return values.stream()
                .filter(value -> credentialId.equals(value.credentialId()))
                .skip(offset)
                .limit(limit)
                .toList();
        }

        @Override
        public long countByCredentialId(UUID credentialId) {
            return values.stream()
                .filter(value -> credentialId.equals(value.credentialId()))
                .count();
        }
    }
}
