package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialHashVersion;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

@Service
public class DefaultCredentialDocumentDownloadService
    implements CredentialDocumentDownloadService {

    private static final Set<CredentialStatus> DOWNLOADABLE = EnumSet.of(
        CredentialStatus.ISSUED,
        CredentialStatus.REVOKED,
        CredentialStatus.SUPERSEDED,
        CredentialStatus.EXPIRED);

    private final CredentialApplicationService credentials;
    private final CredentialProofService proofService;
    private final CredentialHashService hashService;
    private final ObjectMapper objectMapper;

    public DefaultCredentialDocumentDownloadService(
        CredentialApplicationService credentials,
        CredentialProofService proofService,
        CredentialHashService hashService,
        ObjectMapper objectMapper
    ) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.proofService = proofService;
        this.hashService = Objects.requireNonNull(hashService, "hashService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Autowired
    public DefaultCredentialDocumentDownloadService(
        CredentialApplicationService credentials,
        ObjectProvider<CredentialProofService> proofService,
        CredentialHashService hashService,
        ObjectMapper objectMapper
    ) {
        this(credentials, proofService.getIfAvailable(), hashService, objectMapper);
    }

    @Override
    public Download download(UUID credentialId, AuthenticatedUserContext actor) {
        Credential credential = credentials.get(credentialId, actor).credential();
        if (!DOWNLOADABLE.contains(credential.status())) {
            throw new ApiException(HttpStatus.CONFLICT,
                ApiErrorCode.CREDENTIAL_STATE_CONFLICT,
                "Credential document is not available");
        }
        if (credential.vcPayload() == null || credential.vcHash() == null) {
            throw materialInvalid(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (proofService == null) {
            throw materialInvalid(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }
        SignedCredentialEnvelope envelope;
        try {
            envelope = objectMapper.readValue(
                credential.vcPayload(), SignedCredentialEnvelope.class);
        } catch (RuntimeException exception) {
            throw materialInvalid(ApiErrorCode.CREDENTIAL_PROOF_INVALID);
        }
        String calculatedHash = CredentialHashVersion.COMPACT_JWS_SHA256_V1.name()
            .equals(credential.vcHashVersion())
            ? hashService.sha256CompactJws(envelope.compactJws())
            : hashService.sha256Payload(credential.vcPayload());
        if (!calculatedHash.equals(credential.vcHash())) {
            throw materialInvalid(ApiErrorCode.CREDENTIAL_HASH_MISMATCH);
        }
        try {
            proofService.verify(envelope);
        } catch (RuntimeException exception) {
            throw materialInvalid(ApiErrorCode.CREDENTIAL_PROOF_INVALID);
        }
        return new Download(credential.credentialNo(), envelope.compactJws());
    }

    private ApiException materialInvalid(ApiErrorCode code) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, code,
            "Credential document is unavailable");
    }
}
