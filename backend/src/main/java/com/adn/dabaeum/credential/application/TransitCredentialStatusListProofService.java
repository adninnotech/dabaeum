package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService.FailureCode;
import com.adn.dabaeum.credential.domain.CredentialProofService.ProofException;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.StatusListSigningInputFactory;
import com.adn.dabaeum.credential.domain.StatusListSigningPort;
import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * openbao provider 의 Status List 서명 (설계서 §9.1). 기관의 현재 세대 STATUS_LIST 마스터와 그 ACTIVE 버전으로 kid 를
 * 만들고 그 버전을 명시해 상태 역할로 서명한다. 기관 상태는 보지 않는다 — 정지·폐지 기관도 이미 발급한 수료증의
 * 폐기 비트를 갱신해야 한다. Status List 는 요청마다 다시 만들므로 저장할 서명 상태가 없다.
 */
public final class TransitCredentialStatusListProofService implements CredentialStatusListProofService {

    /** 회전 T2 와 min_encryption_version 사이에 고른 버전이 거부되면 한 번만 다시 고른다. */
    static final int MAX_VERSION_RETRIES = 1;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final CredentialUriProvider uriProvider;
    private final StatusListSigningInputFactory inputs;
    private final StatusListSigningPort transit;
    private final CredentialSignatureVerifier verifier;

    public TransitCredentialStatusListProofService(
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        CredentialUriProvider uriProvider,
        StatusListSigningInputFactory inputs,
        StatusListSigningPort transit,
        CredentialSignatureVerifier verifier
    ) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.transit = Objects.requireNonNull(transit, "transit");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    @Override
    public SignedCredentialEnvelope signStatusList(CredentialDocument document) {
        Objects.requireNonNull(document, "document");
        UUID institutionId = issuer(document);
        for (int retries = 0; ; retries++) {
            SelectedKey key = currentKey(institutionId);
            CredentialSigningInput input = inputs.statusListSigningInput(
                document, institutionId, uriProvider.keyId(institutionId, key.version().kidFragment()));
            TransitSignature signature;
            try {
                signature = transit.sign(key.master().mount(), key.master().keyName(),
                    key.version().keyVersion(), input.bytes());
            } catch (TransitSigningException exception) {
                if (exception.reason() == TransitSigningException.Reason.VERSION_REJECTED
                    && retries < MAX_VERSION_RETRIES) {
                    continue;
                }
                throw generationFailed();
            }
            if (signature.keyVersion() != key.version().keyVersion()) {
                throw generationFailed();
            }
            return input.envelope(signature.signature());
        }
    }

    @Override
    public CredentialDocument verifyStatusList(SignedCredentialEnvelope envelope) {
        try {
            return verifier.verifyStatusList(envelope).document();
        } catch (RuntimeException exception) {
            throw new ProofException(FailureCode.CREDENTIAL_PROOF_INVALID, "Credential proof is invalid");
        }
    }

    private SelectedKey currentKey(UUID institutionId) {
        try {
            InstitutionSignKey master = keys.findCurrent(institutionId, SignKeyPurpose.STATUS_LIST)
                .filter(InstitutionSignKey::canSign)
                .orElseThrow(TransitCredentialStatusListProofService::generationFailed);
            InstitutionSignKeyVersion version = versions
                .findBySignKeyIdAndVersion(master.id(), master.currentVersion())
                .filter(row -> row.status() == SignKeyVersionStatus.ACTIVE)
                .orElseThrow(TransitCredentialStatusListProofService::generationFailed);
            return new SelectedKey(master, version);
        } catch (ProofException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw generationFailed();
        }
    }

    /** 문서 issuer 가 플랫폼의 기관 발급자 URL 인지 본다. 화이트리스트 전체 검사는 signing input 생성기가 한다. */
    private UUID issuer(CredentialDocument document) {
        try {
            JsonNode node = JSON.readTree(document.canonicalJson());
            String issuer = node.path("issuer").asString();
            UUID institutionId = UUID.fromString(issuer.substring(issuer.lastIndexOf('/') + 1));
            if (!uriProvider.issuerUrl(institutionId).equals(issuer)) {
                throw generationFailed();
            }
            return institutionId;
        } catch (ProofException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw generationFailed();
        }
    }

    private static ProofException generationFailed() {
        return new ProofException(FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED,
            "Credential proof generation failed");
    }

    private record SelectedKey(InstitutionSignKey master, InstitutionSignKeyVersion version) {
    }
}
