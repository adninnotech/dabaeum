package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KmsOperationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static InstitutionSignKey activeMaster() {
        return InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(3, NOW);
    }

    @Test
    void prepared_는_마스터의_키_기관_목적과_현재_버전을_기록하고_진행_중이다() {
        InstitutionSignKey master = activeMaster();

        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, "2027 정기 교체", REQUESTER, UUID.randomUUID(), "ROTATE-1", NOW);

        assertThat(operation.signKeyId()).isEqualTo(master.id());
        assertThat(operation.institutionId()).isEqualTo(INSTITUTION);
        assertThat(operation.purpose()).isEqualTo(SignKeyPurpose.CREDENTIAL);
        assertThat(operation.status()).isEqualTo(KmsOperationStatus.PREPARED);
        assertThat(operation.expectedVersionBefore()).isEqualTo(3);
        assertThat(operation.preparedAt()).isEqualTo(NOW);
        assertThat(operation.owner()).isNull();
        assertThat(operation.leaseUntil()).isNull();
        assertThat(operation.inProgress()).isTrue();
    }

    @Test
    void 전이는_PREPARED_UNCONFIRMED_RECORDED_COMPLETED_순서만_허용한다() {
        KmsOperation prepared = KmsOperation.prepared(UUID.randomUUID(), activeMaster(), KmsAction.ROTATE,
            null, null, REQUESTER, null, "ROTATE-2", NOW);

        KmsOperation unconfirmed = prepared.unconfirmed(NOW.plusSeconds(1));
        KmsOperation recorded = unconfirmed.recorded(4, "{\"currentVersion\":4}", NOW.plusSeconds(2));
        KmsOperation completed = recorded.completed(NOW.plusSeconds(3));

        assertThat(unconfirmed.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(recorded.status()).isEqualTo(KmsOperationStatus.RECORDED);
        assertThat(recorded.observedVersionAfter()).isEqualTo(4);
        assertThat(recorded.afterValue()).isEqualTo("{\"currentVersion\":4}");
        assertThat(completed.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(completed.inProgress()).isFalse();
        assertThat(prepared.recorded(4, null, NOW).status()).isEqualTo(KmsOperationStatus.RECORDED);
        KmsOperation observed = unconfirmed.observed(5, NOW.plusSeconds(2));
        assertThat(observed.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(observed.observedVersionAfter()).isEqualTo(5);

        assertThatThrownBy(() -> completed.failed("late", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recorded.unconfirmed(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> prepared.completed(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failed_는_PREPARED_나_UNCONFIRMED_에서만_가고_사유를_요구한다() {
        KmsOperation prepared = KmsOperation.prepared(UUID.randomUUID(), activeMaster(), KmsAction.CREATE,
            null, null, REQUESTER, null, "CREATE-1", NOW);

        KmsOperation failedFromPrepared = prepared.failed("never started", NOW.plusSeconds(1));
        assertThat(failedFromPrepared.status()).isEqualTo(KmsOperationStatus.FAILED);
        assertThat(failedFromPrepared.reason()).isEqualTo("never started");

        KmsOperation failed = prepared.unconfirmed(NOW).failed("key not created", NOW.plusSeconds(1));
        assertThat(failed.status()).isEqualTo(KmsOperationStatus.FAILED);
        assertThat(failed.reason()).isEqualTo("key not created");
        assertThatThrownBy(() -> prepared.failed(" ", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completed_팩토리는_외부_호출_없는_행위를_한_번에_기록한다() {
        InstitutionSignKey master = activeMaster();

        KmsOperation operation = KmsOperation.completed(UUID.randomUUID(), master, KmsAction.SUSPEND, null,
            "감사 지적", REQUESTER, null, "SUSPEND-1", "{\"status\":\"ACTIVE\"}", "{\"status\":\"SUSPENDED\"}", NOW);

        assertThat(operation.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(operation.beforeValue()).isEqualTo("{\"status\":\"ACTIVE\"}");
        assertThat(operation.afterValue()).isEqualTo("{\"status\":\"SUSPENDED\"}");
        assertThat(operation.expectedVersionBefore()).isEqualTo(3);
        assertThat(operation.observedVersionAfter()).isEqualTo(3);
    }

    @Test
    void completed_는_target_key_version_을_기록한다() {
        InstitutionSignKey master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        KmsOperation op = KmsOperation.completed(UUID.randomUUID(), master, KmsAction.COMPROMISE, 1, "유출",
            REQUESTER, UUID.randomUUID(), "COMPROMISE-1", "{}", "{}", NOW);

        assertThat(op.targetKeyVersion()).isEqualTo(1);
        assertThat(op.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(op.expectedVersionBefore()).isEqualTo(1);
    }

    @Test
    void observed_는_PREPARED_와_UNCONFIRMED_에서_상태를_바꾸지_않고_관측만_남긴다() {
        InstitutionSignKey master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        KmsOperation prepared = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "R-1", NOW);

        assertThat(prepared.observed(2, NOW).status()).isEqualTo(KmsOperationStatus.PREPARED);
        assertThat(prepared.observed(2, NOW).observedVersionAfter()).isEqualTo(2);
        assertThat(prepared.unconfirmed(NOW).observed(2, NOW).status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThatThrownBy(() -> prepared.recorded(2, "{}", NOW).observed(2, NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void annotated_는_사유_뒤에_덧붙이고_1000자를_넘으면_원래_사유_끝을_자른다() {
        InstitutionSignKey master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        String note = "[adjudicated FAILED by 00000000-0000-0000-0000-0000000000aa at 2026-09-14T00:00:00Z] no request in audit";
        KmsOperation longReason = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            "a".repeat(990), REQUESTER, UUID.randomUUID(), "R-2", NOW);
        KmsOperation noReason = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "R-3", NOW);

        KmsOperation annotated = longReason.annotated(note, NOW);
        assertThat(annotated.reason()).hasSize(KmsOperation.MAX_REASON_LENGTH)
            .startsWith("aaaa").contains("…").endsWith("\n" + note);
        assertThat(noReason.annotated(note, NOW).reason()).isEqualTo(note);
        assertThat(longReason.annotated("short", NOW).status()).isEqualTo(KmsOperationStatus.PREPARED);
        assertThatThrownBy(() -> longReason.annotated(" ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> longReason.annotated("b".repeat(999), NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 소유권_필드는_함께_있거나_함께_없고_멱등키는_비면_안_된다() {
        InstitutionSignKey master = activeMaster();
        KmsOperation base = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, null, REQUESTER, null, "ROTATE-3", NOW);

        assertThatThrownBy(() -> new KmsOperation(base.id(), base.signKeyId(), base.institutionId(), base.purpose(),
            base.action(), base.status(), null, 3, null, NOW, null, "host-1", null, null, null, null, REQUESTER,
            null, "ROTATE-3", NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, null, REQUESTER, null, " ", NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
