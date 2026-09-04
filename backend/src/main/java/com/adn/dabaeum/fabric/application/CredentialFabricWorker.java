package com.adn.dabaeum.fabric.application;

import java.time.Instant;

public interface CredentialFabricWorker {
    int processBatch(int limit, Instant now);
}
