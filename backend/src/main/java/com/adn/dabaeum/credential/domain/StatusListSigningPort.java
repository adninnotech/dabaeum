package com.adn.dabaeum.credential.domain;

/**
 * 상태 역할 토큰으로 education-status 키에 서명한다 (설계서 §9.1, §13.3). 요청 규칙은 {@link TransitSigningPort} 와
 * 같지만 타입을 나눠 발급 역할 토큰과 섞여 주입되지 않게 한다.
 */
@FunctionalInterface
public interface StatusListSigningPort {

    /** @throws TransitSigningException 버전 거부·일시 장애·거부 */
    TransitSignature sign(String mount, String keyName, int keyVersion, byte[] input);
}
