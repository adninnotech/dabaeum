package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
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
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver.KeyResolutionException;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.VerificationKey;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
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
        assertThat(result.issuerName()).isEqualTo("Example Institute");
        assertThat(result.issuedAt()).isEqualTo(fixture.credential.issuedAt());
        assertThat(result.metadata()).contains("requestId");
        assertThat(fixture.repository.records).singleElement().isEqualTo(result);
        assertThat(fixture.gateway.verifyCalls).isEqualTo(1);
    }

    @Test
    void proofFailureAndFabricFailureNeverBecomeValid() {
        Fixture proofFixture = new Fixture();
        proofFixture.proof.invalid = true;
        CredentialVerification invalidProofResult = proofFixture.service.verify(command(proofFixture));
        assertThat(invalidProofResult)
            .extracting(CredentialVerification::result)
            .isEqualTo(CredentialVerificationResult.INVALID);
        assertThat(invalidProofResult.issuerName()).isNull();
        assertThat(invalidProofResult.issuedAt()).isNull();
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
    void publicBaseChangeKeepsACredentialSignedWithARegisteredPastBaseValid() {
        Fixture fixture = new Fixture();
        fixture.proof.document = documentWithPastBaseStatus(fixture);

        assertThat(serviceAtNewBase(fixture, List.of("https://vc.example.test")).verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.VALID);
    }

    @Test
    void publicBaseChangeWithoutRegisteringThePastBaseMakesItInvalid() {
        Fixture fixture = new Fixture();
        fixture.proof.document = documentWithPastBaseStatus(fixture);

        assertThat(serviceAtNewBase(fixture, List.of()).verify(command(fixture)).result())
            .isEqualTo(CredentialVerificationResult.INVALID);
    }

    /** 옛 공개 주소의 상태 URL 이 서명된 문서. 이 값이 있어야 주소 비교를 탄다. */
    private static CredentialDocument documentWithPastBaseStatus(Fixture fixture) {
        return new CredentialDocument("{\"id\":\"urn:uuid:"
            + fixture.credential.id() + "\",\"issuer\":\""
            + fixture.credential.issuerIdentifier() + "\",\"validFrom\":\""
            + fixture.credential.validFrom() + "\",\"validUntil\":\""
            + fixture.credential.validUntil() + "\",\"credentialSubject\":{\"id\":\""
            + fixture.credential.subjectIdentifier() + "\"},\"credentialStatus\":{\"id\":\""
            + "https://vc.example.test/api/v1/vc/status/" + fixture.credential.credentialNo() + "\","
            + "\"type\":\"DabaeumCredentialStatus\"}}");
    }

    private static DefaultCredentialVerificationService serviceAtNewBase(Fixture fixture, List<String> pastBases) {
        return new DefaultCredentialVerificationService(
            fixture.credentials, fixture.repository, fixture.proof,
            new CredentialHashService(fixture.objectMapper), fixture.gateway,
            fixture.application, fixture.objectMapper,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
            new DefaultCredentialUriProvider(new com.adn.dabaeum.credential.config.VcProperties(
                "https://www.new-base.example.test", "v1", "development-1", pastBases)),
            null, RegistryTarget.FABRIC_POC, fixture.signing);
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

    @Test
    void compromisedVersionIsConditionallyAcceptedWhenTheLedgerFirstRecordedItBeforeTheCompromise() {
        Fixture fixture = new Fixture();
        fixture.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        fixture.gateway.history.add(historyEntry(NOW.minusSeconds(10)));
        fixture.gateway.history.add(historyEntry(NOW.minusSeconds(40)));

        CredentialVerification result = fixture.service.verify(command(fixture));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.VALID);
        assertThat(result.platformConditional()).isTrue();
        assertThat(result.metadata()).isEqualTo("{\"verificationScope\":\"PLATFORM_CONDITIONAL\"}");
    }

    @Test
    void compromisedVersionIsRejectedWhenTheLedgerFirstRecordedItAfterTheCompromiseOrNever() {
        Fixture after = new Fixture();
        after.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        after.gateway.history.add(historyEntry(NOW.minusSeconds(20)));
        assertRejected(after, CredentialVerificationResult.INVALID, "KEY_COMPROMISED");

        Fixture never = new Fixture();
        never.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        assertRejected(never, CredentialVerificationResult.INVALID, "KEY_COMPROMISED");

        Fixture notOnLedger = new Fixture();
        notOnLedger.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        notOnLedger.gateway.historyFailureCode = "BLOCKCHAIN_NOT_FOUND";
        assertRejected(notOnLedger, CredentialVerificationResult.INVALID, "KEY_COMPROMISED");
    }

    @Test
    void unreadableLedgerHistoryLeavesACompromisedVersionUndecided() {
        Fixture fixture = new Fixture();
        fixture.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        fixture.gateway.historyFailure = true;

        assertRejected(fixture, CredentialVerificationResult.ERROR, "LEDGER_HISTORY_UNAVAILABLE");
    }

    @Test
    void versionKeyMustBeTheKeyRecordedOnTheCredentialRow() {
        Fixture matching = new Fixture();
        matching.versionKey(2, SignKeyVersionStatus.ACTIVE, null);
        assertThat(matching.service.verify(command(matching)).result())
            .isEqualTo(CredentialVerificationResult.VALID);

        Fixture otherVersion = new Fixture();
        otherVersion.versionKey(2, SignKeyVersionStatus.ACTIVE, null);
        otherVersion.recordSigningKey(SIGN_KEY, 3);
        assertRejected(otherVersion, CredentialVerificationResult.INVALID, "ISSUANCE_NOT_TRUSTED");

        Fixture unrecorded = new Fixture();
        unrecorded.versionKey(2, SignKeyVersionStatus.ACTIVE, null);
        unrecorded.recordSigningKey(null, null);
        assertRejected(unrecorded, CredentialVerificationResult.INVALID, "ISSUANCE_NOT_TRUSTED");

        Fixture unreadable = new Fixture();
        unreadable.versionKey(2, SignKeyVersionStatus.ACTIVE, null);
        unreadable.signing.failure = true;
        assertRejected(unreadable, CredentialVerificationResult.ERROR, "KEY_UNAVAILABLE");
        assertThat(unreadable.gateway.verifyCalls).isZero();
    }

    @Test
    void legacyKeyAcceptsAnEmptyOrVersionZeroReferenceOnly() {
        Fixture fileKeyIssued = new Fixture();
        fileKeyIssued.proof.key = new VerificationKey(PLATFORM_KEY, "development-1", SIGN_KEY, 0,
            SignKeyVersionStatus.RETIRED, null);
        fileKeyIssued.recordSigningKey(null, null);
        assertThat(fileKeyIssued.service.verify(command(fileKeyIssued)).result())
            .isEqualTo(CredentialVerificationResult.VALID);

        Fixture backfilled = new Fixture();
        backfilled.proof.key = fileKeyIssued.proof.key;
        backfilled.recordSigningKey(SIGN_KEY, 0);
        assertThat(backfilled.service.verify(command(backfilled)).result())
            .isEqualTo(CredentialVerificationResult.VALID);

        Fixture otherMaster = new Fixture();
        otherMaster.proof.key = fileKeyIssued.proof.key;
        otherMaster.recordSigningKey(UUID.randomUUID(), 0);
        assertRejected(otherMaster, CredentialVerificationResult.INVALID, "ISSUANCE_NOT_TRUSTED");
    }

    @Test
    void keyAndProofFailuresAreRecordedAsFailureCodesWithoutReachingTheLedger() {
        Fixture purpose = new Fixture();
        purpose.proof.failureCode = KeyResolutionException.PURPOSE_MISMATCH;
        assertRejected(purpose, CredentialVerificationResult.INVALID, "KEY_PURPOSE_MISMATCH");
        assertThat(purpose.gateway.verifyCalls).isZero();

        Fixture unavailable = new Fixture();
        unavailable.proof.generationFailure = true;
        assertRejected(unavailable, CredentialVerificationResult.ERROR, "KEY_UNAVAILABLE");
    }

    @Test
    void platformConditionalScopeIsReadBackFromJsonbNormalizedMetadata() {
        CredentialVerification stored = new CredentialVerification(UUID.randomUUID(), UUID.randomUUID(), "CERT-1",
            null, "API", "INDIVIDUAL", null, CredentialVerificationResult.VALID, NOW, null, null,
            "{\"requestId\": \"1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1\", \"verificationScope\": \"PLATFORM_CONDITIONAL\"}",
            NOW);
        CredentialVerification plain = new CredentialVerification(UUID.randomUUID(), UUID.randomUUID(), "CERT-1",
            null, "API", "INDIVIDUAL", null, CredentialVerificationResult.VALID, NOW, null, null, "{}", NOW);

        assertThat(stored.platformConditional()).isTrue();
        assertThat(plain.platformConditional()).isFalse();
    }

    @Test
    void verifierBeanAbsentFailsWithKeyUnavailable() {
        Fixture fixture = new Fixture();
        DefaultCredentialVerificationService service = new DefaultCredentialVerificationService(
            fixture.credentials, fixture.repository, null,
            new CredentialHashService(fixture.objectMapper), fixture.gateway,
            fixture.application, fixture.objectMapper,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
            new DefaultCredentialUriProvider(new com.adn.dabaeum.credential.config.VcProperties(
                "https://vc.example.test", "v1", "development-1")),
            null, RegistryTarget.FABRIC_POC, fixture.signing);

        CredentialVerification result = service.verify(command(fixture));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.ERROR);
        assertThat(result.metadata()).contains("\"failureCode\":\"KEY_UNAVAILABLE\"");
        assertThat(result.platformConditional()).isFalse();
    }

    @Test
    void signingRepositoryAbsentWithVersionRowKeyFailsWithKeyUnavailable() {
        Fixture fixture = new Fixture();
        fixture.versionKey(2, SignKeyVersionStatus.ACTIVE, null);
        DefaultCredentialVerificationService service = new DefaultCredentialVerificationService(
            fixture.credentials, fixture.repository, fixture.proof,
            new CredentialHashService(fixture.objectMapper), fixture.gateway,
            fixture.application, fixture.objectMapper,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
            new DefaultCredentialUriProvider(new com.adn.dabaeum.credential.config.VcProperties(
                "https://vc.example.test", "v1", "development-1")),
            null, RegistryTarget.FABRIC_POC, null);

        CredentialVerification result = service.verify(command(fixture));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.ERROR);
        assertThat(result.metadata()).contains("\"failureCode\":\"KEY_UNAVAILABLE\"");
        assertThat(result.platformConditional()).isFalse();
    }

    @Test
    void ledgerPortAbsentWithCompromisedKeyFailsWithLedgerHistoryUnavailable() {
        Fixture fixture = new Fixture();
        fixture.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        DefaultCredentialVerificationService service = new DefaultCredentialVerificationService(
            fixture.credentials, fixture.repository, fixture.proof,
            new CredentialHashService(fixture.objectMapper), null,
            fixture.application, fixture.objectMapper,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
            new DefaultCredentialUriProvider(new com.adn.dabaeum.credential.config.VcProperties(
                "https://vc.example.test", "v1", "development-1")),
            null, RegistryTarget.FABRIC_POC, fixture.signing);

        CredentialVerification result = service.verify(command(fixture));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.ERROR);
        assertThat(result.metadata()).contains("\"failureCode\":\"LEDGER_HISTORY_UNAVAILABLE\"");
        assertThat(result.platformConditional()).isFalse();
    }

    @Test
    void conditionalScopeIsNotRecordedWhenTheFinalLedgerJudgmentIsUndecided() {
        Fixture fixture = new Fixture();
        fixture.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        fixture.gateway.history.add(historyEntry(NOW.minusSeconds(40)));
        fixture.gateway.failure = true;

        CredentialVerification result = fixture.service.verify(command(fixture));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.ERROR);
        assertThat(result.metadata()).isEqualTo("{}");
        assertThat(result.platformConditional()).isFalse();
    }

    @Test
    void conditionalScopeIsRecordedWhenTheFinalResultIsRevoked() {
        Fixture fixture = new Fixture(CredentialStatus.REVOKED);
        fixture.gateway.status = RegistryStatus.REVOKED;
        fixture.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        fixture.gateway.history.add(historyEntry(NOW.minusSeconds(40)));

        CredentialVerification result = fixture.service.verify(command(fixture));

        assertThat(result.result()).isEqualTo(CredentialVerificationResult.REVOKED);
        assertThat(result.platformConditional()).isTrue();
        assertThat(result.metadata()).isEqualTo("{\"verificationScope\":\"PLATFORM_CONDITIONAL\"}");
    }

    @Test
    void ledgerAnchorExactlyAtTheCompromiseInstantIsNotAcceptedYet() {
        Fixture fixture = new Fixture();
        fixture.versionKey(1, SignKeyVersionStatus.COMPROMISED, NOW.minusSeconds(30));
        fixture.gateway.history.add(historyEntry(NOW.minusSeconds(30)));

        assertRejected(fixture, CredentialVerificationResult.INVALID, "KEY_COMPROMISED");
    }

    @Test
    void existingCredentialMissingFromTheLedgerIsRecordedAsInvalidInsteadOfFailing() {
        Fixture fixture = new Fixture();
        fixture.gateway.exists = false;

        assertRejected(fixture, CredentialVerificationResult.INVALID, "LEDGER_RECORD_NOT_FOUND");
        assertThat(fixture.gateway.verifyCalls).isEqualTo(1);
        assertThat(fixture.repository.records).hasSize(1);
    }

    private static void assertRejected(Fixture fixture, CredentialVerificationResult result, String failureCode) {
        CredentialVerification verification = fixture.service.verify(command(fixture));
        assertThat(verification.result()).isEqualTo(result);
        assertThat(verification.metadata()).contains("\"failureCode\":\"" + failureCode + "\"");
        assertThat(verification.platformConditional()).isFalse();
    }

    private static BlockchainHistoryEntry historyEntry(Instant at) {
        return new BlockchainHistoryEntry("tx-" + at.getEpochSecond(),
            new CredentialRegistryState("CERT:CERT-VERIFY-001", 1, RegistryStatus.ACTIVE, "a".repeat(64),
                at, BlockchainProvider.FABRIC_POC), at, false);
    }

    private static CredentialVerifyCommand command(Fixture fixture) {
        return new CredentialVerifyCommand(fixture.credential.credentialNo(), null,
            "API", "INDIVIDUAL", null, NOW);
    }

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SIGN_KEY = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final PublicKey PLATFORM_KEY = generatedKey();

    private static PublicKey generatedKey() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class Fixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final InMemoryVerificationRepository repository = new InMemoryVerificationRepository();
        private final FakeCredentialRepository credentials = new FakeCredentialRepository();
        private final FakeProof proof = new FakeProof();
        private final FakeRegistry gateway = new FakeRegistry();
        private final FakeSigning signing = new FakeSigning();
        private final InstitutionRepository institutions = mock(InstitutionRepository.class);
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
            UUID institutionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            when(institutions.findById(institutionId)).thenReturn(Optional.of(new Institution(
                institutionId, "EXAMPLE", "Example Institute", null, null, null, null, null,
                InstitutionStatus.ACTIVE, issuedAt, issuedAt, null)));
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
                    "https://vc.example.test", "v1", "development-1")),
                null, RegistryTarget.FABRIC_POC, signing, institutions);
        }

        private void versionKey(int keyVersion, SignKeyVersionStatus status, Instant compromisedAt) {
            proof.key = new VerificationKey(PLATFORM_KEY, "v" + keyVersion, SIGN_KEY, keyVersion, status, compromisedAt);
            recordSigningKey(SIGN_KEY, keyVersion);
        }

        private void recordSigningKey(UUID signKeyId, Integer keyVersion) {
            signing.state = Optional.of(new CredentialSigningState(credential.id(), CredentialStatus.ISSUED,
                signKeyId, keyVersion, signKeyId == null ? null : 1, "aGVhZGVy", "cGF5bG9hZA", "{}"));
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

    private static final class FakeProof implements CredentialSignatureVerifier {
        private boolean invalid;
        /** 키를 읽지 못하는 상황. 예전 파일 키 서비스의 GENERATION_FAILED 자리다. */
        private boolean generationFailure;
        private String failureCode;
        private CredentialDocument document;
        private VerificationKey key = VerificationKey.legacyFile(PLATFORM_KEY);

        @Override
        public VerifiedDocument verifyCredential(com.adn.dabaeum.credential.domain.SignedCredentialEnvelope envelope) {
            if (invalid) throw new VerificationFailure(VerificationFailure.PROOF_INVALID);
            if (generationFailure) throw new VerificationFailure(KeyResolutionException.UNAVAILABLE);
            if (failureCode != null) throw new VerificationFailure(failureCode);
            return new VerifiedDocument(document, INSTITUTION, key);
        }

        @Override
        public VerifiedDocument verifyStatusList(com.adn.dabaeum.credential.domain.SignedCredentialEnvelope envelope) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeSigning implements CredentialSigningRepository {
        private Optional<CredentialSigningState> state = Optional.empty();
        private boolean failure;

        @Override public Optional<CredentialSigningState> findSigningState(UUID credentialId) {
            if (failure) throw new IllegalStateException("database unavailable");
            return state;
        }
        @Override public Optional<UUID> findInstitutionId(UUID credentialId) { throw new UnsupportedOperationException(); }
        @Override public boolean commitSigningInput(UUID credentialId, UUID signKeyId, int signKeyVersion,
            CredentialSigningInput input, String issuerIdentifier, String subjectIdentifier, Instant issuedAt, Instant now) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean recommitHeader(UUID credentialId, int expectedAttemptNo, UUID signKeyId,
            int signKeyVersion, CredentialSigningInput input, Instant now) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean storeSignature(UUID credentialId, int attemptNo, UUID signKeyId, int signKeyVersion,
            CredentialSigningInput input, String vcPayload, String vcHash, Instant now) {
            throw new UnsupportedOperationException();
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
        private final List<BlockchainHistoryEntry> history = new ArrayList<>();
        private boolean historyFailure;
        private String historyFailureCode;
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
                                                                            BlockchainHistoryQuery query) {
            if (historyFailure) throw new IllegalStateException("ledger history unavailable");
            if (historyFailureCode != null) {
                throw new com.adn.dabaeum.blockchain.domain.BlockchainRegistryException(historyFailureCode);
            }
            assertThat(query.reverse()).isFalse();
            assertThat(query.offset()).isZero();
            return history;
        }
    }
}
