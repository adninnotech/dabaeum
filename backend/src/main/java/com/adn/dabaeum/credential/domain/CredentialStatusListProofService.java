package com.adn.dabaeum.credential.domain;

/** BitstringStatusListCredential 을 발급기관 키로 서명·검증한다. 수료증 VC 와 같은 키·헤더를 쓴다. */
public interface CredentialStatusListProofService {

    SignedCredentialEnvelope signStatusList(CredentialDocument document);

    CredentialDocument verifyStatusList(SignedCredentialEnvelope envelope);
}
