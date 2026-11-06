package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §6.1 버전 상태, §6.5 유출 선언, §9.2 전환 이전 키 호환 행. */
class InstitutionSignKeyVersionTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";
    private static final UUID SIGN_KEY = UUID.randomUUID();
    private static final UUID INSTITUTION = UUID.randomUUID();

    @Test
    void active_팩토리는_kid_조각을_규칙으로_만든다() {
        InstitutionSignKeyVersion v3 = InstitutionSignKeyVersion.active(UUID.randomUUID(), SIGN_KEY,
            INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 3, JWK, NOW.minusSeconds(5), null, NOW);

        assertThat(v3.kidFragment()).isEqualTo("v3");
        assertThat(v3.status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(v3.source()).isEqualTo(InstitutionSignKeyVersion.Source.OPENBAO);
        assertThat(v3.openbaoCreatedAt()).isEqualTo(NOW.minusSeconds(5));
        assertThat(v3.activatedAt()).isEqualTo(NOW);
    }

    @Test
    void legacy_팩토리는_버전_0_development_1_RETIRED_LOCAL_FILE_이다() {
        InstitutionSignKeyVersion legacy = InstitutionSignKeyVersion.legacy(
            UUID.randomUUID(), SIGN_KEY, INSTITUTION, JWK, NOW);

        assertThat(legacy.keyVersion()).isZero();
        assertThat(legacy.kidFragment()).isEqualTo("development-1");
        assertThat(legacy.status()).isEqualTo(SignKeyVersionStatus.RETIRED);
        assertThat(legacy.source()).isEqualTo(InstitutionSignKeyVersion.Source.LOCAL_FILE);
        assertThat(legacy.retiredAt()).isEqualTo(NOW);
        assertThat(legacy.openbaoCreatedAt()).isNull();
    }

    @Test
    void retire_는_ACTIVE_에서만_되고_compromise_는_ACTIVE_와_RETIRED_에서_된다() {
        InstitutionSignKeyVersion active = InstitutionSignKeyVersion.active(UUID.randomUUID(),
            SIGN_KEY, INSTITUTION, SignKeyPurpose.STATUS_LIST, 1, 1, JWK, null, null, NOW);

        InstitutionSignKeyVersion retired = active.retire(NOW.plusSeconds(1));
        assertThat(retired.status()).isEqualTo(SignKeyVersionStatus.RETIRED);
        assertThat(retired.retiredAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatIllegalStateException().isThrownBy(() -> retired.retire(NOW));

        InstitutionSignKeyVersion fromRetired = retired.compromise(NOW.plusSeconds(2));
        InstitutionSignKeyVersion fromActive = active.compromise(NOW.plusSeconds(2));
        assertThat(fromRetired.status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(fromActive.compromisedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void COMPROMISED_는_어떤_전이로도_바뀌지_않는다() {
        InstitutionSignKeyVersion compromised = InstitutionSignKeyVersion.active(UUID.randomUUID(),
            SIGN_KEY, INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, null, null, NOW)
            .compromise(NOW.plusSeconds(1));

        assertThatIllegalStateException().isThrownBy(() -> compromised.retire(NOW));
        assertThatIllegalStateException().isThrownBy(() -> compromised.compromise(NOW));
    }

    @Test
    void 공개키_JWK_에는_kid_를_넣지_않는다() {
        assertThatIllegalArgumentException().isThrownBy(() -> InstitutionSignKeyVersion.active(
            UUID.randomUUID(), SIGN_KEY, INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1,
            "{\"kid\":\"x\",\"kty\":\"OKP\"}", null, null, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> InstitutionSignKeyVersion.active(
            UUID.randomUUID(), SIGN_KEY, INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1,
            " ", null, null, NOW));
    }
}
