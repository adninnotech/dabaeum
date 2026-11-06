package com.adn.dabaeum.credential.domain;

/** 키 관리 행위 (설계서 §11.4). */
public enum KmsAction {
    CREATE,
    ROTATE,
    SUSPEND,
    RESUME,
    REVOKE,
    COMPROMISE
}
