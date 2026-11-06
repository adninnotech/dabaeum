package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.SigningKeyCommands.CreateKeys;
import com.adn.dabaeum.credential.domain.PendingSigningKeyRequest;
import com.adn.dabaeum.credential.domain.SigningKeyRequestQueue;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstitutionSigningKeyProvisionerTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant LEASE_UNTIL = NOW.plus(InstitutionSigningKeyProvisioner.LEASE);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String KEY_NAME = "institution-" + INSTITUTION_ID;

    @Mock SigningKeyRequestQueue queue;
    @Mock SigningKeyProvisioningService provisioning;
    @Mock TransitKeyAdminPort keyAdmin;

    private InstitutionSigningKeyProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new InstitutionSigningKeyProvisioner(
            queue, provisioning, keyAdmin, Clock.fixed(NOW, ZoneOffset.UTC));
        when(queue.markDone(any(), any(), any())).thenReturn(true);
        when(queue.markFailed(any(), any(), anyInt(), any())).thenReturn(true);
        when(queue.reschedule(any(), any(), anyInt(), any(), any())).thenReturn(true);
    }

    @Test
    void createsKeysAfterReachableOpenBaoAndMarksRequestDone() {
        givenClaimed(0);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenReturn(Optional.empty());

        provisioner.runOnce(10);

        ArgumentCaptor<CreateKeys> command = ArgumentCaptor.forClass(CreateKeys.class);
        verify(provisioning).createKeys(command.capture());
        assertThat(command.getValue().institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(command.getValue().requester()).isEqualTo(ADMIN_ID);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("institution-auto-" + EVENT_ID);
        verify(queue).markDone(EVENT_ID, LEASE_UNTIL, NOW);
    }

    @Test
    void claimsWithALeaseSoOtherServersSkipTheRequestWhileOpenBaoIsCalled() {
        when(queue.claim(10, NOW, LEASE_UNTIL)).thenReturn(List.of());

        provisioner.runOnce(10);

        verify(queue).claim(10, NOW, LEASE_UNTIL);
        verifyNoInteractions(provisioning, keyAdmin);
    }

    @Test
    void treatsExistingKeysAsDone() {
        givenClaimed(0);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenReturn(Optional.empty());
        when(provisioning.createKeys(any())).thenThrow(
            new SigningKeyException(SigningKeyException.Kind.ALREADY_EXISTS, "exists"));

        provisioner.runOnce(10);

        verify(queue).markDone(EVENT_ID, LEASE_UNTIL, NOW);
    }

    @ParameterizedTest
    @CsvSource({"0, 1, 1", "1, 2, 5", "2, 3, 30", "3, 4, 120"})
    void unreachableOpenBaoIsRescheduledWithoutStartingAnOperation(
        int retryCount, int nextRetryCount, int delayMinutes
    ) {
        givenClaimed(retryCount);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenThrow(unavailable());

        provisioner.runOnce(10);

        verify(provisioning, never()).createKeys(any());
        verify(queue).reschedule(EVENT_ID, LEASE_UNTIL, nextRetryCount,
            NOW.plus(Duration.ofMinutes(delayMinutes)), "OPENBAO_UNAVAILABLE");
    }

    @Test
    void fifthUnreachableAttemptFailsTheRequest() {
        givenClaimed(4);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenThrow(unavailable());

        provisioner.runOnce(10);

        verify(queue).markFailed(EVENT_ID, LEASE_UNTIL, 5, "OPENBAO_UNAVAILABLE");
        verify(queue, never()).reschedule(any(), any(), anyInt(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"0, 1, 1", "3, 4, 120", "4, 5, 120", "9, 10, 120"})
    void loginFailureKeepsRetryingInsteadOfFailingSoARenewedSecretIdRecovers(
        int retryCount, int nextRetryCount, int delayMinutes
    ) {
        givenClaimed(retryCount);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenThrow(new TransitKeyAdminException(
            TransitKeyAdminException.Reason.AUTHENTICATION_FAILED, "login failed", null));

        provisioner.runOnce(10);

        verify(provisioning, never()).createKeys(any());
        verify(queue).reschedule(EVENT_ID, LEASE_UNTIL, nextRetryCount,
            NOW.plus(Duration.ofMinutes(delayMinutes)), "OPENBAO_LOGIN_FAILED");
        verify(queue, never()).markFailed(any(), any(), anyInt(), any());
    }

    @Test
    void rejectedPreflightFailsImmediately() {
        givenClaimed(0);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenThrow(new TransitKeyAdminException(
            TransitKeyAdminException.Reason.REJECTED, "permission denied", null));

        provisioner.runOnce(10);

        verify(provisioning, never()).createKeys(any());
        verify(queue).markFailed(EVENT_ID, LEASE_UNTIL, 0, "OPENBAO_REJECTED");
    }

    @ParameterizedTest
    @EnumSource(value = SigningKeyException.Kind.class, names = {
        "KMS_UNAVAILABLE", "IDEMPOTENCY_CONFLICT", "STATE_CONFLICT", "NOT_FOUND"})
    void failedOrUncertainCreationIsNotRetried(SigningKeyException.Kind kind) {
        givenClaimed(0);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenReturn(Optional.empty());
        when(provisioning.createKeys(any())).thenThrow(
            new SigningKeyException(kind, "failed", UUID.randomUUID()));

        provisioner.runOnce(10);

        verify(queue).markFailed(EVENT_ID, LEASE_UNTIL, 0, "SIGNING_KEY_" + kind.name());
        verify(queue, never()).reschedule(any(), any(), anyInt(), any(), any());
    }

    @Test
    void unexpectedErrorIsRetriedWithBackoffAndOtherRequestsContinue() {
        UUID otherEvent = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID otherInstitution = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(queue.claim(10, NOW, LEASE_UNTIL)).thenReturn(List.of(
            new PendingSigningKeyRequest(EVENT_ID, INSTITUTION_ID, ADMIN_ID, 0),
            new PendingSigningKeyRequest(otherEvent, otherInstitution, ADMIN_ID, 0)));
        when(keyAdmin.readKey(any(), any())).thenReturn(Optional.empty());
        when(provisioning.createKeys(any()))
            .thenThrow(new IllegalStateException("db down"))
            .thenReturn(List.of());

        provisioner.runOnce(10);

        verify(queue).reschedule(EVENT_ID, LEASE_UNTIL, 1, NOW.plus(Duration.ofMinutes(1)), "UNEXPECTED_ERROR");
        verify(queue).markDone(otherEvent, LEASE_UNTIL, NOW);
    }

    @Test
    void failureToRecordSuccessLeavesTheLeaseToExpireInsteadOfFailing() {
        givenClaimed(0);
        when(keyAdmin.readKey("education-sign", KEY_NAME)).thenReturn(Optional.empty());
        when(queue.markDone(EVENT_ID, LEASE_UNTIL, NOW)).thenThrow(new IllegalStateException("db down"));

        provisioner.runOnce(10);

        verify(queue, never()).markFailed(any(), any(), anyInt(), any());
        verify(queue, never()).reschedule(any(), any(), anyInt(), any(), any());
    }

    private void givenClaimed(int retryCount) {
        when(queue.claim(10, NOW, LEASE_UNTIL)).thenReturn(List.of(
            new PendingSigningKeyRequest(EVENT_ID, INSTITUTION_ID, ADMIN_ID, retryCount)));
    }

    private static TransitKeyAdminException unavailable() {
        return new TransitKeyAdminException(
            TransitKeyAdminException.Reason.UNAVAILABLE, "sealed", null);
    }
}
