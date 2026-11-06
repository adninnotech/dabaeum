package com.adn.dabaeum.credential.domain;

import java.util.UUID;

/**
 * VC 문서와 kid 로 JWS signing input 을 만든다. 헤더 직렬화와 payload 정규성 검증은 파일 키 서명과 같은
 * 규칙이다 (설계서 §7.2).
 */
@FunctionalInterface
public interface CredentialSigningInputFactory {

    /**
     * @param institutionId 서명 키를 가진 기관. payload 의 issuer 와 kid 가 모두 이 기관이어야 한다
     * @param keyId 헤더 kid 전체 값 ({@code {issuerUrl}#{kidFragment}})
     * @throws CredentialProofService.ProofException 정규 W3C 수료증이 아니거나 기관이 어긋날 때
     */
    CredentialSigningInput credentialSigningInput(
        CredentialDocument document, UUID institutionId, String keyId);
}
