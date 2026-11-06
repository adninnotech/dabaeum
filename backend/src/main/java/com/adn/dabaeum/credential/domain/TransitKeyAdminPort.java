package com.adn.dabaeum.credential.domain;

import java.util.Optional;

/**
 * 키 관리자 역할로 부르는 Transit 키 관리 (설계서 §5.3, §6.2, §13.4). 허용 경로는 keys/{name},
 * keys/{name}/rotate, keys/{name}/config 뿐이다. 변경 호출의 결과는 응답이 아니라 {@link #readKey} 로 확인한다.
 */
public interface TransitKeyAdminPort {

    /** {@code type=ed25519} 만 보낸다. 같은 이름에 다시 보내도 새 버전이 생기지 않는다. */
    void createKey(String mount, String keyName);

    /** 없으면 empty. */
    Optional<TransitKeyInfo> readKey(String mount, String keyName);

    void rotateKey(String mount, String keyName);

    /** @param minEncryptionVersion 1 이상. 호출자가 {@code max(현재, 목표)} 를 계산한다 (§6.2) */
    void setMinEncryptionVersion(String mount, String keyName, int minEncryptionVersion);
}
