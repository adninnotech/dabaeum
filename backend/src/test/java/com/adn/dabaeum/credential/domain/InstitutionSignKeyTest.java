package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §6 상태도와 §6.1 마스터 상태 규칙. */
class InstitutionSignKeyTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID INSTITUTION = UUID.randomUUID();

    @Test
    void provisioning_팩토리는_첫_세대_키_이름과_mount_를_정한다() {
        InstitutionSignKey key = provisioning(SignKeyPurpose.CREDENTIAL);

        assertThat(key.status()).isEqualTo(SignKeyStatus.PROVISIONING);
        assertThat(key.generation()).isEqualTo(1);
        assertThat(key.isCurrent()).isTrue();
        assertThat(key.mount()).isEqualTo("education-sign");
        assertThat(key.keyName()).isEqualTo("institution-" + INSTITUTION);
        assertThat(key.keyType()).isEqualTo("ed25519");
        assertThat(key.currentVersion()).isZero();
        assertThat(key.versionNo()).isEqualTo(1);
    }

    @Test
    void 생성_검증이_끝나면_ACTIVE_와_버전_1_이_되고_실패하면_ERROR_다() {
        InstitutionSignKey key = provisioning(SignKeyPurpose.CREDENTIAL);

        InstitutionSignKey active = key.activate(1, NOW.plusSeconds(1));
        assertThat(active.status()).isEqualTo(SignKeyStatus.ACTIVE);
        assertThat(active.currentVersion()).isEqualTo(1);
        assertThat(active.versionNo()).isEqualTo(2);

        InstitutionSignKey error = key.markError("auto_rotate_period is not 0", NOW.plusSeconds(1));
        assertThat(error.status()).isEqualTo(SignKeyStatus.ERROR);
        assertThat(error.statusReason()).isEqualTo("auto_rotate_period is not 0");

        assertThatIllegalStateException().isThrownBy(() -> active.activate(2, NOW));
    }

    @Test
    void rotation_은_ACTIVE_에서만_현재_버전을_한_단계_올린다() {
        InstitutionSignKey active = provisioning(SignKeyPurpose.CREDENTIAL).activate(1, NOW);

        InstitutionSignKey rotated = active.rotated(2, NOW.plusSeconds(1));
        assertThat(rotated.currentVersion()).isEqualTo(2);
        assertThat(rotated.rotatedAt()).isEqualTo(NOW.plusSeconds(1));

        assertThatIllegalStateException().isThrownBy(() -> active.rotated(3, NOW));
        assertThatIllegalStateException()
            .isThrownBy(() -> active.suspend("audit", NOW).rotated(2, NOW));
    }

    @Test
    void 수료증_키만_정지_재개_폐지가_된다() {
        InstitutionSignKey credential = provisioning(SignKeyPurpose.CREDENTIAL).activate(1, NOW);
        InstitutionSignKey statusList = provisioning(SignKeyPurpose.STATUS_LIST).activate(1, NOW);

        InstitutionSignKey suspended = credential.suspend("audit", NOW.plusSeconds(1));
        assertThat(suspended.status()).isEqualTo(SignKeyStatus.SUSPENDED);
        assertThat(suspended.suspendedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(suspended.resume(NOW.plusSeconds(2)).status()).isEqualTo(SignKeyStatus.ACTIVE);

        InstitutionSignKey revoked = suspended.revoke("closed", NOW.plusSeconds(3));
        assertThat(revoked.status()).isEqualTo(SignKeyStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(revoked.statusReason()).isEqualTo("closed");

        assertThatIllegalStateException().isThrownBy(() -> statusList.suspend("audit", NOW));
        assertThatIllegalStateException().isThrownBy(() -> statusList.revoke("closed", NOW));
        assertThatIllegalStateException().isThrownBy(() -> revoked.resume(NOW));
        assertThatIllegalStateException().isThrownBy(() -> credential.resume(NOW));
    }

    @Test
    void 마스터_유출은_현재_세대에서_내려오고_되돌릴_수_없다() {
        InstitutionSignKey active = provisioning(SignKeyPurpose.CREDENTIAL).activate(1, NOW);

        InstitutionSignKey compromised = active.compromise("openbao breach", NOW.plusSeconds(1));
        assertThat(compromised.status()).isEqualTo(SignKeyStatus.COMPROMISED);
        assertThat(compromised.isCurrent()).isFalse();
        assertThat(compromised.compromisedAt()).isEqualTo(NOW.plusSeconds(1));

        assertThatIllegalStateException().isThrownBy(() -> compromised.resume(NOW));
        assertThatIllegalStateException().isThrownBy(() -> compromised.rotated(2, NOW));
        assertThatIllegalStateException().isThrownBy(() -> compromised.suspend("x", NOW));
        assertThatIllegalStateException().isThrownBy(() -> compromised.compromise("again", NOW));
    }

    @Test
    void 레코드_불변식을_검사한다() {
        assertThatIllegalStateException().isThrownBy(() -> new InstitutionSignKey(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.STATUS_LIST, 1, true,
            "education-status", "institution-" + INSTITUTION, "ed25519", 1,
            SignKeyStatus.SUSPENDED, null, null, NOW, null, null, 1, NOW, NOW));
        assertThatIllegalStateException().isThrownBy(() -> new InstitutionSignKey(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, true,
            "education-sign", "institution-" + INSTITUTION, "ed25519", 1,
            SignKeyStatus.COMPROMISED, "x", null, null, null, NOW, 1, NOW, NOW));
    }

    @Test
    void ERROR_마스터는_retryProvisioning_으로_PROVISIONING_에_돌아오고_다른_상태는_거부한다() {
        InstitutionSignKey error = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW).markError("key not created", NOW);

        InstitutionSignKey retried = error.retryProvisioning(NOW);

        assertThat(retried.status()).isEqualTo(SignKeyStatus.PROVISIONING);
        assertThat(retried.statusReason()).isNull();
        assertThat(retried.keyName()).isEqualTo(error.keyName());
        assertThat(retried.isCurrent()).isTrue();
        assertThat(retried.versionNo()).isEqualTo(error.versionNo() + 1);
        assertThatThrownBy(() -> retried.retryProvisioning(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> retried.activate(1, NOW).retryProvisioning(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    private InstitutionSignKey provisioning(SignKeyPurpose purpose) {
        return InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, purpose, 1, NOW);
    }
}
