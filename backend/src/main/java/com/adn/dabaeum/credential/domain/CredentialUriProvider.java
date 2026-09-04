package com.adn.dabaeum.credential.domain;

import java.util.UUID;

public interface CredentialUriProvider {

    String contextUrl();

    String vocabularyUrl();

    String issuerUrl(UUID institutionId);

    String keyId(UUID institutionId);

    /** 전환 이전 발급분의 credentialStatus.id. 그 VC 들을 계속 검증하기 위해 유지한다. */
    String statusUrl(String credentialNo);

    /** BitstringStatusListCredential 의 공개 URL. VC 의 statusListCredential 이 가리킨다. */
    String statusListUrl(UUID listId);
}
