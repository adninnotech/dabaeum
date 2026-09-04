package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import java.util.UUID;

public interface CredentialVerificationService {
    CredentialVerification verify(CredentialVerifyCommand command);

    CredentialVerificationPage list(
        UUID credentialId, int page, int size, String sort, AuthenticatedUserContext actor);
}
