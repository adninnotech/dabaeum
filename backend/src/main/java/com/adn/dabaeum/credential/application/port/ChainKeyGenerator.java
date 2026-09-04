package com.adn.dabaeum.credential.application.port;

/** 신규 Storage 호환 Credential의 논리 원장 키를 생성한다. */
@FunctionalInterface
public interface ChainKeyGenerator {

    String generate();
}
