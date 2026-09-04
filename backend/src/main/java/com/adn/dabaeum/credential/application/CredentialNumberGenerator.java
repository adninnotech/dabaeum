package com.adn.dabaeum.credential.application;

import java.util.UUID;

public interface CredentialNumberGenerator {

    String generate(UUID credentialId);
}
