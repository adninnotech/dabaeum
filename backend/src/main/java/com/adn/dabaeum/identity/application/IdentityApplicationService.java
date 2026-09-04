package com.adn.dabaeum.identity.application;

import com.adn.dabaeum.identity.domain.UserIdentity;
import java.util.List;
import java.util.UUID;

public interface IdentityApplicationService {

    List<UserIdentity> list(UUID userId);

    UserIdentity link(LinkIdentityCommand command);

    UserIdentity unlink(UUID userId, UUID identityId);
}
