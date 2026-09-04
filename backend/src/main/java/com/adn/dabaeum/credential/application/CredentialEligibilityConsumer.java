package com.adn.dabaeum.credential.application;

import java.time.Instant;

public interface CredentialEligibilityConsumer {

    int consumeBatch(int limit, Instant now);
}
