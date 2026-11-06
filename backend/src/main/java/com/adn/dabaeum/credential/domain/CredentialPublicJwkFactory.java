package com.adn.dabaeum.credential.domain;

import java.security.PublicKey;
import java.util.Map;

public interface CredentialPublicJwkFactory {

    Map<String, String> create(String keyId, PublicKey publicKey);

    /**
     * 버전 행에 보존한 JWK(kid 없음)를 공개키로 되돌린다 (설계서 §9.2). 저장소가 jsonb 에서 돌려주므로 필드
     * 순서와 공백은 보지 않는다.
     *
     * @throws IllegalArgumentException Ed25519 서명 공개키 다섯 필드가 아니면
     */
    PublicKey publicKey(String storedJwk);

    /**
     * 보존한 JWK 를 발급자 문서용으로 만든다. 필드 순서는 {@link #create} 와 같은 kid·kty·crv·alg·use·x 다.
     *
     * @throws IllegalArgumentException kid 가 비었거나 저장값이 Ed25519 서명 공개키가 아니면
     */
    Map<String, String> publish(String keyId, String storedJwk);
}
