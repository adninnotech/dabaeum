package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §5.2 키 이름, §6.2 kid 조각 규칙. */
class SignKeyNamingTest {

    private static final UUID INSTITUTION = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void 첫_세대_키_이름은_접미가_없고_다음_세대부터_g가_붙는다() {
        assertThat(SignKeyNaming.keyName(INSTITUTION, 1))
            .isEqualTo("institution-11111111-2222-3333-4444-555555555555");
        assertThat(SignKeyNaming.keyName(INSTITUTION, 2))
            .isEqualTo("institution-11111111-2222-3333-4444-555555555555-g2");
        assertThatIllegalArgumentException().isThrownBy(() -> SignKeyNaming.keyName(INSTITUTION, 0));
    }

    @Test
    void kid_조각은_목적과_세대에_따라_접두가_달라진다() {
        assertThat(SignKeyNaming.kidFragment(SignKeyPurpose.CREDENTIAL, 1, 3)).isEqualTo("v3");
        assertThat(SignKeyNaming.kidFragment(SignKeyPurpose.STATUS_LIST, 1, 2)).isEqualTo("status-v2");
        assertThat(SignKeyNaming.kidFragment(SignKeyPurpose.CREDENTIAL, 2, 1)).isEqualTo("g2-v1");
        assertThat(SignKeyNaming.kidFragment(SignKeyPurpose.STATUS_LIST, 3, 1)).isEqualTo("status-g3-v1");
    }

    @Test
    void 버전_0은_전환_이전_플랫폼_키_조각으로만_쓴다() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SignKeyNaming.kidFragment(SignKeyPurpose.CREDENTIAL, 1, 0));
        assertThat(SignKeyNaming.LEGACY_KID_FRAGMENT).isEqualTo("development-1");
        assertThat(SignKeyNaming.LEGACY_KEY_VERSION).isZero();
    }

    @Test
    void 목적별_mount_는_고정이다() {
        assertThat(SignKeyPurpose.CREDENTIAL.mount()).isEqualTo("education-sign");
        assertThat(SignKeyPurpose.STATUS_LIST.mount()).isEqualTo("education-status");
    }
}
