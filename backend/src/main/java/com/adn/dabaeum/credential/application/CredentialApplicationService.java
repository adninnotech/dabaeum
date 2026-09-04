package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialView;
import java.util.UUID;

public interface CredentialApplicationService {

    Credential issue(IssueCredentialCommand command);

    Credential revoke(RevokeCredentialCommand command);

    Credential reissue(ReissueCredentialCommand command);

    CredentialView get(UUID credentialId, AuthenticatedUserContext actor);

    CredentialPage listByUser(ListUserCredentialsQuery query);
}
