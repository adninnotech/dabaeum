package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;

class PublicCredentialStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private final CredentialVerificationService verifications =
        mock(CredentialVerificationService.class);
    private final PublicCredentialStatusService service = new DefaultPublicCredentialStatusService(
        verifications, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void mapsEveryVerificationResultAndCreatesApiIndividualAudit() {
        for (CredentialVerificationResult result : CredentialVerificationResult.values()) {
            when(verifications.verify(any())).thenReturn(verification(result));

            PublicCredentialStatusService.Status resultView = service.check(
                "CERT-VERIFY-001", "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1");

            assertThat(resultView.credentialNo()).isEqualTo("CERT-VERIFY-001");
            assertThat(resultView.status()).isEqualTo(result);
            assertThat(resultView.checkedAt()).isEqualTo(NOW);
        }

        ArgumentCaptor<CredentialVerifyCommand> command =
            ArgumentCaptor.forClass(CredentialVerifyCommand.class);
        verify(verifications, org.mockito.Mockito.atLeastOnce()).verify(command.capture());
        assertThat(command.getValue()).satisfies(value -> {
            assertThat(value.credentialNo()).isEqualTo("CERT-VERIFY-001");
            assertThat(value.credentialHash()).isNull();
            assertThat(value.verificationType()).isEqualTo("API");
            assertThat(value.requesterType()).isEqualTo("INDIVIDUAL");
            assertThat(value.requestId()).isEqualTo("1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1");
            assertThat(value.requestedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void rejectsUnsafeCredentialNumberBeforeVerification() {
        assertThatThrownBy(() -> service.check("CERT/../1", null))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /** Fake Provider 의 결과가 공개 상태로 나가지 않도록 실제 원장 Provider 가 붙었을 때만 뜬다. */
    @Test
    void serviceRequiresLocalOrDevTogetherWithTheDaeguchainProvider() {
        Profile profile = DefaultPublicCredentialStatusService.class.getAnnotation(Profile.class);
        var condition = DefaultPublicCredentialStatusService.class.getAnnotation(
            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local", "dev");
        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("dabaeum.blockchain");
        assertThat(condition.name()).containsExactly("provider");
        assertThat(condition.havingValue()).isEqualTo("daeguchain");
    }

    private CredentialVerification verification(CredentialVerificationResult result) {
        UUID credentialId = result == CredentialVerificationResult.NOT_FOUND
            ? null : UUID.randomUUID();
        return new CredentialVerification(UUID.randomUUID(), credentialId,
            "CERT-VERIFY-001", null, "API", "INDIVIDUAL", null, result,
            NOW, null, null, "{}", NOW);
    }
}
