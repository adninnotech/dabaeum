package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialView;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReissue;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class CredentialVerificationServiceTest {

    @Test
    void verificationContractExposesResultAndAuditRepository() {
        assertThat(CredentialVerificationResult.values())
            .containsExactly(
                CredentialVerificationResult.VALID,
                CredentialVerificationResult.INVALID,
                CredentialVerificationResult.REVOKED,
                CredentialVerificationResult.SUPERSEDED,
                CredentialVerificationResult.EXPIRED,
                CredentialVerificationResult.NOT_FOUND,
                CredentialVerificationResult.ERROR);
        assertThat(CredentialVerificationRepository.class.getMethods())
            .extracting(java.lang.reflect.Method::getName)
            .contains("insert", "findByCredentialId", "countByCredentialId");
    }

    @Test
    void validVerificationRequiresProofHashAndFabricAndStoresSafeAudit() throws Exception {
        Fixture fixture = new Fixture();

        CredentialVerification result = fixture.service.verify(new CredentialVerifyCommand(
            fixture.credential.credentialNo(), null, "API", "SYSTEM",
            "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1", NOW));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.VALID);
        assertThat(result.requesterType()).isEqualTo("INDIVIDUAL");
        assertThat(result.requesterId()).isNull();
        assertThat(result.requestIp()).isNull();
        assertThat(result.metadata()).contains("requestId");
        assertThat(fixture.repository.records).singleElement().isEqualTo(result);
        assertThat(fixture.gateway.verifyCalls).isEqualTo(1);
    }

    @Test
    void proofFailureAndFabricFailureNeverBecomeValid() {
        Fixture proofFixture = new Fixture();
        proofFixture.proof.invalid = true;
        assertThat(proofFixture.service.verify(command(proofFixture)))
            .extracting(CredentialVerification::result)
            .isEqualTo(CredentialVerificationResult.INVALID);
        assertThat(proofFixture.gateway.verifyCalls).isZero();

        Fixture fabricFixture = new Fixture();
        fabricFixture.gateway.failure = true;
        assertThat(fabricFixture.service.verify(command(fabricFixture)))
            .extracting(CredentialVerification::result)
            .isEqualTo(CredentialVerificationResult.ERROR);

        Fixture keyFailure = new Fixture();
        keyFailure.proof.generationFailure = true;
        assertThat(keyFailure.service.verify(command(keyFailure)))
            .extracting(CredentialVerification::result)
            .isEqualTo(CredentialVerificationResult.ERROR);
    }

    @Test
    void whitespaceMutationOfPersistedEnvelopeFailsRawHashCheck() {
        Fixture fixture = new Fixture(true);

        assertThat(fixture.service.verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);
        assertThat(fixture.gateway.verifyCalls).isZero();
    }

    @Test
    void currentCredentialHashesTheExactCompactJwsInsteadOfEnvelopeJson() {
        Fixture fixture = new Fixture(true, true);

        assertThat(fixture.service.verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.VALID);
        assertThat(fixture.gateway.verifyCalls).isEqualTo(1);
    }

    @Test
    void explicitLegacyHashVersionUsesTheEnvelopeHashRule() {
        Fixture fixture = new Fixture(
            CredentialStatus.ISSUED, NOW.minusSeconds(60), null, false, false, true);

        assertThat(fixture.service.verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.VALID);
    }

    @Test
    void statusAndIdentifierOutcomesAreRecorded() {
        Fixture revoked = new Fixture(CredentialStatus.REVOKED);
        revoked.gateway.status = RegistryStatus.REVOKED;
        assertThat(revoked.service.verify(command(revoked)).result())
            .isEqualTo(CredentialVerificationResult.REVOKED);

        Fixture superseded = new Fixture(CredentialStatus.SUPERSEDED);
        superseded.gateway.status = RegistryStatus.REVOKED;
        assertThat(superseded.service.verify(command(superseded)).result())
            .isEqualTo(CredentialVerificationResult.SUPERSEDED);
        Fixture expired = new Fixture(CredentialStatus.EXPIRED);
        assertThat(expired.service.verify(command(expired)).result())
            .isEqualTo(CredentialVerificationResult.EXPIRED);

        Fixture missing = new Fixture();
        missing.credentials.byNo = Optional.empty();
        assertThat(missing.service.verify(new CredentialVerifyCommand(
            "CERT-MISSING", null, "API", "INDIVIDUAL", null, NOW)).result())
            .isEqualTo(CredentialVerificationResult.NOT_FOUND);
        assertThat(missing.repository.records).singleElement()
            .satisfies(record -> assertThat(record.credentialId()).isNull());
    }

    @Test
    void terminalDatabaseStatusesRequireMatchingFabricEvidence() {
        Fixture revoked = new Fixture(CredentialStatus.REVOKED);
        revoked.gateway.failure = true;

        assertThat(revoked.service.verify(command(revoked)).result())
            .isEqualTo(CredentialVerificationResult.ERROR);
        assertThat(revoked.gateway.verifyCalls).isEqualTo(1);

        Fixture superseded = new Fixture(CredentialStatus.SUPERSEDED);
        assertThat(superseded.service.verify(command(superseded)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);
        assertThat(superseded.gateway.verifyCalls).isEqualTo(1);

        Fixture expired = new Fixture(CredentialStatus.EXPIRED);
        expired.gateway.status = RegistryStatus.REVOKED;
        assertThat(expired.service.verify(command(expired)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);
        assertThat(expired.gateway.verifyCalls).isEqualTo(1);
    }

    @Test
    void fabricEvidenceMustMatchCredentialIdentityAndHash() {
        Fixture fixture = new Fixture();
        fixture.gateway.hashMatched = false;
        assertThat(fixture.service.verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);

        Fixture wrongId = new Fixture();
        wrongId.gateway.chainKey = "CERT:CERT-OTHER";
        assertThat(wrongId.service.verify(command(wrongId)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);
    }

    @Test
    void credentialStatusUrlMustMatchPersistedCredentialNumber() {
        Fixture fixture = new Fixture();
        fixture.proof.document = new CredentialDocument("{\"id\":\"urn:uuid:"
            + fixture.credential.id() + "\",\"issuer\":\""
            + fixture.credential.issuerIdentifier() + "\",\"validFrom\":\""
            + fixture.credential.validFrom() + "\",\"validUntil\":\""
            + fixture.credential.validUntil() + "\",\"credentialSubject\":{\"id\":\""
            + fixture.credential.subjectIdentifier() + "\"},\"credentialStatus\":{\"id\":\""
            + "https://vc.example.test/api/v1/vc/status/CERT-OTHER\","
            + "\"type\":\"DabaeumCredentialStatus\"}}");

        assertThat(fixture.service.verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);
        assertThat(fixture.gateway.verifyCalls).isZero();
    }

    @Test
    void databaseMicrosecondRoundingDoesNotInvalidateBoundCredential() {
        Fixture fixture = new Fixture(CredentialStatus.ISSUED,
            Instant.parse("2026-08-06T23:59:00.123456789Z"),
            "2026-08-06T23:59:00.123457Z");

        assertThat(fixture.service.verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.VALID);
    }

    @Test
    void verificationDomainRejectsBlankPresentedIdentifier() {
        assertThatThrownBy(() -> new CredentialVerification(
            UUID.randomUUID(), null, "   ", null, "API", "INDIVIDUAL", null,
            CredentialVerificationResult.NOT_FOUND, NOW, null, null, "{}", NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyListRechecksCredentialScopeAndRejectsUnlistedSorts() {
        Fixture fixture = new Fixture();
        AuthenticatedUserContext actor = new AuthenticatedUserContext(
            UUID.randomUUID(), "LOCAL", java.util.Set.of(
                new AuthenticatedRole("INSTITUTION_ADMIN", UUID.randomUUID())));

        CredentialVerificationPage page = fixture.service.list(
            fixture.credential.id(), 0, 20, "verifiedAt,desc", actor);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        verify(fixture.application).get(fixture.credential.id(), actor);

        assertThatThrownBy(() -> fixture.service.list(
            fixture.credential.id(), 0, 20, "issuedAt,desc", actor))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).status())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static CredentialVerifyCommand command(Fixture fixture) {
        return new CredentialVerifyCommand(fixture.credential.credentialNo(), null,
            "API", "INDIVIDUAL", null, NOW);
    }

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    private static final class Fixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final InMemoryVerificationRepository repository = new InMemoryVerificationRepository();
        private final FakeCredentialRepository credentials = new FakeCredentialRepository();
        private final FakeProof proof = new FakeProof();
        private final FakeRegistry gateway = new FakeRegistry();
        private final CredentialApplicationService application = mock(CredentialApplicationService.class);
        private final Credential credential;
        private final CredentialVerificationService service;

        private Fixture() { this(CredentialStatus.ISSUED); }

        private Fixture(CredentialStatus status) {
            this(status, NOW.minusSeconds(60), null, false);
        }

        private Fixture(CredentialStatus status, Instant validFrom, String documentValidFrom) {
            this(status, validFrom, documentValidFrom, false);
        }

        private Fixture(boolean whitespacePayload) {
            this(CredentialStatus.ISSUED, NOW.minusSeconds(60), null, whitespacePayload);
        }

        private Fixture(boolean whitespacePayload, boolean current) {
            this(CredentialStatus.ISSUED, NOW.minusSeconds(60), null, whitespacePayload, current);
        }

        private Fixture(CredentialStatus status, Instant validFrom, String documentValidFrom,
                        boolean whitespacePayload) {
            this(status, validFrom, documentValidFrom, whitespacePayload, false);
        }

        private Fixture(CredentialStatus status, Instant validFrom, String documentValidFrom,
                        boolean whitespacePayload, boolean current) {
            this(status, validFrom, documentValidFrom, whitespacePayload, current, false);
        }

        private Fixture(CredentialStatus status, Instant validFrom, String documentValidFrom,
                        boolean whitespacePayload, boolean current, boolean explicitLegacyVersion) {
            SignedCredentialEnvelopeHolder envelope = envelope();
            String payload;
            try {
                payload = whitespacePayload
                    ? "{\"mediaType\": \"application/vc+jwt\", "
                        + "\"compactJws\":\"header.payload.signature\"}"
                    : objectMapper.writeValueAsString(envelope.value);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            String hash = current
                ? new CredentialHashService(objectMapper).sha256CompactJws(envelope.value.compactJws())
                : new CredentialHashService(objectMapper).sha256(envelope.value);
            Instant issuedAt = validFrom;
            credential = new Credential(UUID.randomUUID(), UUID.randomUUID(), null,
                "CERT-VERIFY-001", 1, "urn:dabaeum:institution:00000000-0000-0000-0000-000000000001",
                "urn:dabaeum:user:00000000-0000-0000-0000-000000000002",
                "LIFELONG_EDUCATION_COMPLETION", status, issuedAt, NOW.plusSeconds(3600),
                payload, hash, current ? "CURRENTKEY000001" : null,
                current ? "COMPACT_JWS_SHA256_V1"
                    : explicitLegacyVersion ? "ENVELOPE_SHA256_V0" : null, issuedAt,
                status == CredentialStatus.REVOKED ? NOW : null,
                status == CredentialStatus.REVOKED ? "administrative correction" : null,
                null, null, issuedAt, issuedAt);
            proof.document = new CredentialDocument("{\"id\":\"urn:uuid:" + credential.id()
                + "\",\"issuer\":\"" + credential.issuerIdentifier()
                + "\",\"validFrom\":\"" + (documentValidFrom == null
                    ? credential.validFrom() : documentValidFrom)
                + "\",\"validUntil\":\"" + credential.validUntil()
                + "\",\"credentialSubject\":{\"id\":\""
                + credential.subjectIdentifier() + "\"}}");
            credentials.byNo = Optional.of(credential);
            credentials.byHash = Optional.of(credential);
            gateway.expectedHash = credential.vcHash();
            when(application.get(any(), any())).thenReturn(new CredentialView(credential, null));
            service = new DefaultCredentialVerificationService(credentials, repository, proof,
                new CredentialHashService(objectMapper), gateway,
                application, objectMapper, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
                new DefaultCredentialUriProvider(new com.adn.dabaeum.credential.config.VcProperties(
                    "https://vc.example.test", "v1", "development-1")));
        }

        private SignedCredentialEnvelopeHolder envelope() {
            return new SignedCredentialEnvelopeHolder(
                new com.adn.dabaeum.credential.domain.SignedCredentialEnvelope(
                    "application/vc+jwt", "header.payload.signature"));
        }
    }

    private record SignedCredentialEnvelopeHolder(
        com.adn.dabaeum.credential.domain.SignedCredentialEnvelope value
    ) { }

    private static final class InMemoryVerificationRepository
        implements CredentialVerificationRepository {
        private final List<CredentialVerification> records = new ArrayList<>();

        @Override public void insert(CredentialVerification verification) { records.add(verification); }
        @Override public List<CredentialVerification> findByCredentialId(UUID credentialId, int limit,
                                                                          int offset, String sort) {
            return List.of();
        }
        @Override public long countByCredentialId(UUID credentialId) { return records.size(); }
    }

    private static final class FakeCredentialRepository implements CredentialRepository {
        private Optional<Credential> byNo = Optional.empty();
        private Optional<Credential> byHash = Optional.empty();
        @Override public Optional<Credential> findById(UUID id) { return byNo.filter(c -> c.id().equals(id)); }
        @Override public Optional<Credential> findByCredentialNo(String no) { return byNo.filter(c -> c.credentialNo().equals(no)); }
        @Override public Optional<Credential> findByCredentialHash(String hash) { return byHash.filter(c -> c.vcHash().equals(hash)); }
        @Override public Optional<Credential> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public Optional<Credential> findActiveByGroupId(UUID id) { return Optional.empty(); }
        @Override public List<Credential> findByUserId(UUID id, int limit, int offset, String sort) { return List.of(); }
        @Override public long countByUserId(UUID id) { return 0; }
        @Override public int nextVersionForUpdate(UUID id) { return 1; }
        @Override public void insert(Credential credential) { }
        @Override public boolean insertIfChainKeyAvailable(Credential credential) { insert(credential); return true; }
        @Override public void update(Credential credential) { }
    }

    private static final class FakeProof implements CredentialProofService {
        private boolean invalid;
        private boolean generationFailure;
        private CredentialDocument document;
        @Override public com.adn.dabaeum.credential.domain.SignedCredentialEnvelope sign(CredentialDocument document) { throw new UnsupportedOperationException(); }
        @Override public CredentialDocument verify(com.adn.dabaeum.credential.domain.SignedCredentialEnvelope envelope) {
            if (invalid) throw new ProofException(FailureCode.CREDENTIAL_PROOF_INVALID, "invalid");
            if (generationFailure) throw new ProofException(FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED, "key unavailable");
            return document;
        }
    }

    private static final class FakeRegistry implements BlockchainRegistryPort {
        private boolean failure;
        private boolean exists = true;
        private boolean hashMatched = true;
        private RegistryStatus status = RegistryStatus.ACTIVE;
        private String chainKey;
        private String expectedHash;
        private int verifyCalls;
        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) { throw new UnsupportedOperationException(); }
        @Override public CredentialRegistryState getCredentialState(CredentialRegistryReference reference) {
            verifyCalls++;
            if (failure) throw new IllegalStateException("gateway unavailable");
            String resultKey = chainKey == null ? reference.chainKey() : chainKey;
            if (!exists) throw new com.adn.dabaeum.blockchain.domain.BlockchainRegistryException(
                "BLOCKCHAIN_NOT_FOUND");
            String hash = hashMatched ? expectedHash : "f".repeat(64);
            return new CredentialRegistryState(resultKey, 1, status, hash,
                NOW.minusSeconds(1), BlockchainProvider.FABRIC_POC);
        }
        @Override public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) { throw new UnsupportedOperationException(); }
        @Override public BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command) { throw new UnsupportedOperationException(); }
        @Override public List<BlockchainHistoryEntry> getCredentialHistory(CredentialRegistryReference reference,
                                                                            BlockchainHistoryQuery query) { return List.of(); }
    }
}
