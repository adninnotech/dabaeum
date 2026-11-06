package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KmsApprovalTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final String HASH = "a".repeat(64);

    private static InstitutionSignKey master() {
        return InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(2, NOW);
    }

    @Test
    void create_는_현재_버전과_세대를_고정하고_24시간_뒤_만료한다() {
        InstitutionSignKey master = master();

        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            HASH, APPROVER, NOW);

        assertThat(approval.signKeyId()).isEqualTo(master.id());
        assertThat(approval.institutionId()).isEqualTo(INSTITUTION);
        assertThat(approval.purpose()).isEqualTo(SignKeyPurpose.CREDENTIAL);
        assertThat(approval.expectedVersion()).isEqualTo(2);
        assertThat(approval.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(approval.isOpen(NOW.plus(Duration.ofHours(23)))).isTrue();
        assertThat(approval.isOpen(NOW.plus(Duration.ofHours(24)))).isFalse();
        assertThat(approval.consumedAt()).isNull();
    }

    @Test
    void COMPROMISE_만_대상_버전과_사고_시각을_요구하고_다른_행위는_금지한다() {
        InstitutionSignKey master = master();

        KmsApproval compromise = KmsApproval.create(UUID.randomUUID(), master, KmsAction.COMPROMISE, 1,
            NOW.minusSeconds(3600), HASH, APPROVER, NOW);
        assertThat(compromise.targetKeyVersion()).isEqualTo(1);
        assertThat(compromise.targetCompromisedAt()).isEqualTo(NOW.minusSeconds(3600));

        assertThatThrownBy(() -> KmsApproval.create(UUID.randomUUID(), master, KmsAction.COMPROMISE, null,
            NOW, HASH, APPROVER, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, 1,
            null, HASH, APPROVER, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 승인이_필요_없는_행위는_승인_기록을_만들_수_없다() {
        assertThat(KmsApproval.requiresApproval(KmsAction.CREATE)).isFalse();
        assertThat(KmsApproval.requiresApproval(KmsAction.SUSPEND)).isFalse();
        assertThat(KmsApproval.requiresApproval(KmsAction.RESUME)).isTrue();
        assertThat(KmsApproval.requiresApproval(KmsAction.REVOKE)).isTrue();
        assertThatThrownBy(() -> KmsApproval.create(UUID.randomUUID(), master(), KmsAction.CREATE, null, null,
            HASH, APPROVER, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void params_hash_는_소문자_hex_64자여야_하고_소비_필드는_함께_있어야_한다() {
        InstitutionSignKey master = master();
        assertThatThrownBy(() -> KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            "A".repeat(64), APPROVER, NOW)).isInstanceOf(IllegalArgumentException.class);
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            HASH, APPROVER, NOW);
        assertThatThrownBy(() -> new KmsApproval(approval.id(), approval.institutionId(), approval.purpose(),
            approval.action(), approval.signKeyId(), approval.expectedVersion(), null, null, HASH, APPROVER,
            approval.expiresAt(), NOW, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void params_hash_는_행위_목적_멱등키를_개행으로_이어_SHA256_한_값이다() {
        String hash = KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, "ROTATE-2027");

        assertThat(hash).matches("^[0-9a-f]{64}$");
        assertThat(hash).isEqualTo(KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, "ROTATE-2027"));
        assertThat(hash).isNotEqualTo(KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.STATUS_LIST, "ROTATE-2027"));
        assertThat(hash).isNotEqualTo(KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, "ROTATE-2028"));
        assertThatThrownBy(() -> KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
