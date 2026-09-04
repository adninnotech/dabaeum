package com.adn.dabaeum.credential.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class DefaultCredentialNumberGenerator implements CredentialNumberGenerator {

    @Override
    public String generate(UUID credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return "CERT-" + credentialId.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }
}
