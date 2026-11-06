package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.fabric.application.SigningKeyFinalizeCheck.Outcome;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 설계서 §8.1 10단계: 버전 행 FOR SHARE, 기관·마스터 상태를 같은 트랜잭션에서 확인한다. */
class DefaultSigningKeyFinalizeCheckTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID CREDENTIAL = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private final CredentialSigningRepository signing = mock(CredentialSigningRepository.class);
    private final InstitutionSignKeyRepository keys = mock(InstitutionSignKeyRepository.class);
    private final InstitutionSignKeyVersionRepository versions = mock(InstitutionSignKeyVersionRepository.class);
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final DefaultSigningKeyFinalizeCheck check =
        new DefaultSigningKeyFinalizeCheck(signing, keys, versions, institutions);

    @Test
    void 파일_키로_서명한_발급분은_키를_보지_않는다() {
        when(signing.findSigningState(CREDENTIAL)).thenReturn(Optional.of(
            new CredentialSigningState(CREDENTIAL, CredentialStatus.ISSUING, null, null, null, null, null, "{}")));

        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.ALLOW);
        verifyNoInteractions(versions, keys, institutions);
    }

    @Test
    void 버전_행을_FOR_SHARE_로_읽고_COMPROMISED_면_거부한다() {
        InstitutionSignKey master = signedWith(active(), 2, SignKeyVersionStatus.COMPROMISED);

        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.COMPROMISED);
        verify(versions).findStatusForShare(master.id(), 2);
    }

    @Test
    void 마스터가_COMPROMISED_면_버전_행이_ACTIVE_여도_거부한다() {
        signedWith(active().compromise("openbao breach", NOW), 1, SignKeyVersionStatus.ACTIVE);

        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.COMPROMISED);
    }

    @Test
    void 마스터_정지_또는_기관_비활성은_INACTIVE_다() {
        signedWith(active().suspend("audit", NOW), 1, SignKeyVersionStatus.ACTIVE);
        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.INACTIVE);

        signedWith(active(), 1, SignKeyVersionStatus.ACTIVE);
        institution(InstitutionStatus.SUSPENDED, null);
        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.INACTIVE);

        institution(InstitutionStatus.ACTIVE, NOW);
        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.INACTIVE);
    }

    @Test
    void 활성_키와_백필한_버전_0_은_통과한다() {
        signedWith(active(), 1, SignKeyVersionStatus.ACTIVE);
        institution(InstitutionStatus.ACTIVE, null);
        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.ALLOW);

        signedWith(InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW),
            0, SignKeyVersionStatus.RETIRED);
        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.ALLOW);
    }

    @Test
    void 코드는_원장_제출_전과_확정에서_다르다() {
        assertThat(SigningKeyFinalizeCheck.beforeSubmissionCode(Outcome.COMPROMISED))
            .isEqualTo("CREDENTIAL_SIGNING_KEY_COMPROMISED");
        assertThat(SigningKeyFinalizeCheck.beforeSubmissionCode(Outcome.INACTIVE))
            .isEqualTo("CREDENTIAL_SIGNING_KEY_INACTIVE");
        assertThat(SigningKeyFinalizeCheck.finalizeCode(Outcome.COMPROMISED)).isEqualTo("COMPROMISED_AT_FINALIZE");
        assertThat(SigningKeyFinalizeCheck.finalizeCode(Outcome.INACTIVE))
            .isEqualTo("SIGNING_KEY_INACTIVE_AT_FINALIZE");
    }

    @Test
    void 버전_행을_마스터와_기관보다_먼저_FOR_SHARE_로_읽는다() {
        InstitutionSignKey master = active();
        UUID keyId = master.id();
        int keyVersion = 1;

        when(signing.findSigningState(CREDENTIAL)).thenReturn(Optional.of(new CredentialSigningState(
            CREDENTIAL, CredentialStatus.ISSUING, keyId, keyVersion, 1, "aGVhZGVy", "cGF5bG9hZA", "{}")));
        when(versions.findStatusForShare(keyId, keyVersion)).thenReturn(Optional.of(SignKeyVersionStatus.ACTIVE));
        when(keys.findById(keyId)).thenReturn(Optional.of(master));
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(new Institution(
            INSTITUTION, "INST-1", "기관", null, null, null, null, null, InstitutionStatus.ACTIVE, NOW, NOW, null)));

        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.ALLOW);

        InOrder order = inOrder(versions, keys, institutions);
        order.verify(versions).findStatusForShare(keyId, keyVersion);
        order.verify(keys).findById(keyId);
        order.verify(institutions).findById(INSTITUTION);
    }

    @Test
    void 마스터가_폐지됐으면_INACTIVE_다() {
        InstitutionSignKey master = active().revoke("legal order", NOW);

        signedWith(master, 1, SignKeyVersionStatus.ACTIVE);

        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.INACTIVE);
    }

    @Test
    void 기관이_없으면_INACTIVE_다() {
        InstitutionSignKey master = active();
        UUID keyId = master.id();
        int keyVersion = 1;

        when(signing.findSigningState(CREDENTIAL)).thenReturn(Optional.of(new CredentialSigningState(
            CREDENTIAL, CredentialStatus.ISSUING, keyId, keyVersion, 1, "aGVhZGVy", "cGF5bG9hZA", "{}")));
        when(versions.findStatusForShare(keyId, keyVersion)).thenReturn(Optional.of(SignKeyVersionStatus.ACTIVE));
        when(keys.findById(keyId)).thenReturn(Optional.of(master));
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.empty());

        assertThat(check.check(CREDENTIAL)).isEqualTo(Outcome.INACTIVE);
    }

    private InstitutionSignKey active() {
        return InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(1, NOW);
    }

    private InstitutionSignKey signedWith(InstitutionSignKey master, int keyVersion, SignKeyVersionStatus status) {
        when(signing.findSigningState(CREDENTIAL)).thenReturn(Optional.of(new CredentialSigningState(
            CREDENTIAL, CredentialStatus.ISSUING, master.id(), keyVersion, 1, "aGVhZGVy", "cGF5bG9hZA", "{}")));
        when(versions.findStatusForShare(master.id(), keyVersion)).thenReturn(Optional.of(status));
        when(keys.findById(master.id())).thenReturn(Optional.of(master));
        institution(InstitutionStatus.ACTIVE, null);
        return master;
    }

    private void institution(InstitutionStatus status, Instant deletedAt) {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(new Institution(
            INSTITUTION, "INST-1", "기관", null, null, null, null, null, status, NOW, NOW, deletedAt)));
    }
}
