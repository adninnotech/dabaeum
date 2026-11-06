package com.adn.dabaeum.credential.domain;

/**
 * OpenBao Transit 서명 (설계서 §7.2, §7.3). key_version 을 반드시 명시하고 signing input 원문을 보낸다.
 * 응답 버전이 요청과 같은지는 호출자가 검사한다.
 */
public interface TransitSigningPort {

    /** @throws TransitSigningException 버전 거부·일시 장애·요청 거부 */
    TransitSignature sign(String mount, String keyName, int keyVersion, byte[] signingInput);
}
