package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DefaultCredentialUriProvider implements CredentialUriProvider {

    private static final Pattern CREDENTIAL_NUMBER =
        Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");

    private final VcProperties properties;

    public DefaultCredentialUriProvider(VcProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String contextUrl() {
        return properties.publicBaseUrl()
            + "/api/v1/vc/contexts/lifelong-education/" + properties.contextVersion();
    }

    @Override
    public String vocabularyUrl() {
        return properties.publicBaseUrl()
            + "/api/v1/vc/vocabulary/lifelong-education/" + properties.contextVersion();
    }

    @Override
    public String issuerUrl(UUID institutionId) {
        return properties.publicBaseUrl() + "/api/v1/vc/issuers/"
            + Objects.requireNonNull(institutionId, "institutionId");
    }

    @Override
    public String keyId(UUID institutionId) {
        return issuerUrl(institutionId) + "#" + properties.keyVersion();
    }

    @Override
    public String statusUrl(String credentialNo) {
        Objects.requireNonNull(credentialNo, "credentialNo");
        if (!CREDENTIAL_NUMBER.matcher(credentialNo).matches()) {
            throw new IllegalArgumentException("credentialNo format is invalid");
        }
        return properties.publicBaseUrl() + "/api/v1/vc/status/" + credentialNo;
    }

    @Override
    public String statusListUrl(UUID listId) {
        return properties.publicBaseUrl() + "/api/v1/vc/status-lists/"
            + Objects.requireNonNull(listId, "listId");
    }
}
