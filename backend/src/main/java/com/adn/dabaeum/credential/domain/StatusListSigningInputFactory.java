package com.adn.dabaeum.credential.domain;

import java.util.UUID;

/** openbao provider 의 Status List signing input 생성기 (설계서 §9.1). */
@FunctionalInterface
public interface StatusListSigningInputFactory {

    /**
     * @param keyId 그 기관 Status List 버전 행 조각으로 만든 kid
     * @throws CredentialProofService.ProofException CREDENTIAL_PROOF_GENERATION_FAILED
     */
    CredentialSigningInput statusListSigningInput(CredentialDocument document, UUID institutionId, String keyId);
}
