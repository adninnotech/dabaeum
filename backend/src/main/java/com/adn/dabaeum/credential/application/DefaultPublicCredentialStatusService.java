package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 실제 원장(대구체인 계약)이 붙어 있을 때만 공개 상태를 낸다. Fake Provider 의 결과를
 * 공개 상태로 내보내지 않기 위한 게이트다 (ADR-0005).
 */
@Service
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "dabaeum.blockchain", name = "provider", havingValue = "daeguchain")
public class DefaultPublicCredentialStatusService implements PublicCredentialStatusService {

    private static final Pattern CREDENTIAL_NUMBER =
        Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");

    private final CredentialVerificationService verificationService;
    private final Clock clock;

    public DefaultPublicCredentialStatusService(
        CredentialVerificationService verificationService,
        Clock clock
    ) {
        this.verificationService = Objects.requireNonNull(
            verificationService, "verificationService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Status check(String credentialNo, String requestId) {
        if (credentialNo == null || !CREDENTIAL_NUMBER.matcher(credentialNo).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("credentialNo"));
        }
        Instant requestedAt = clock.instant();
        CredentialVerification verification = verificationService.verify(
            new CredentialVerifyCommand(credentialNo, null, "API", "INDIVIDUAL",
                requestId, requestedAt));
        return new Status(credentialNo, verification.result(), verification.verifiedAt());
    }
}
