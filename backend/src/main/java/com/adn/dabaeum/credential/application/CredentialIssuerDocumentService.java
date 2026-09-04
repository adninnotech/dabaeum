package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CredentialIssuerDocumentService {

    private final InstitutionRepository institutions;
    private final CredentialUriProvider uriProvider;
    private final ObjectProvider<PublicKey> publicKeyProvider;
    private final CredentialPublicJwkFactory jwkFactory;

    public CredentialIssuerDocumentService(
        InstitutionRepository institutions,
        CredentialUriProvider uriProvider,
        ObjectProvider<PublicKey> publicKeyProvider,
        CredentialPublicJwkFactory jwkFactory
    ) {
        this.institutions = Objects.requireNonNull(institutions, "institutions");
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
        this.publicKeyProvider = Objects.requireNonNull(publicKeyProvider, "publicKeyProvider");
        this.jwkFactory = Objects.requireNonNull(jwkFactory, "jwkFactory");
    }

    public Map<String, Object> document(UUID institutionId) {
        Objects.requireNonNull(institutionId, "institutionId");
        institutions.findById(institutionId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.INSTITUTION_NOT_FOUND, "Institution not found"));

        PublicKey publicKey = publicKeyProvider.getIfAvailable();
        if (publicKey == null) {
            throw unavailable();
        }
        String issuer = uriProvider.issuerUrl(institutionId);
        String keyId = uriProvider.keyId(institutionId);
        Map<String, String> jwk;
        try {
            jwk = jwkFactory.create(keyId, publicKey);
        } catch (RuntimeException exception) {
            throw unavailable();
        }

        Map<String, Object> verificationMethod = new LinkedHashMap<>();
        verificationMethod.put("id", keyId);
        verificationMethod.put("type", "JsonWebKey");
        verificationMethod.put("controller", issuer);
        verificationMethod.put("publicKeyJwk", jwk);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", "https://www.w3.org/ns/cid/v1");
        document.put("id", issuer);
        document.put("verificationMethod", List.of(verificationMethod));
        document.put("assertionMethod", List.of(keyId));
        return document;
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            ApiErrorCode.INTERNAL_SERVER_ERROR, "Credential issuer key is unavailable");
    }
}
