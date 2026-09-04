package com.adn.dabaeum.credential.domain;

public interface CredentialProofService {

    SignedCredentialEnvelope sign(CredentialDocument document);

    CredentialDocument verify(SignedCredentialEnvelope envelope);

    enum FailureCode {
        CREDENTIAL_PROOF_GENERATION_FAILED,
        CREDENTIAL_PROOF_INVALID
    }

    final class ProofException extends RuntimeException {

        private final FailureCode failureCode;

        public ProofException(FailureCode failureCode, String message) {
            super(message);
            this.failureCode = failureCode;
        }

        public FailureCode failureCode() {
            return failureCode;
        }
    }
}
