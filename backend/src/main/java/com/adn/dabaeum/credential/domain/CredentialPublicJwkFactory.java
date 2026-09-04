package com.adn.dabaeum.credential.domain;

import java.security.PublicKey;
import java.util.Map;

public interface CredentialPublicJwkFactory {

    Map<String, String> create(String keyId, PublicKey publicKey);
}
