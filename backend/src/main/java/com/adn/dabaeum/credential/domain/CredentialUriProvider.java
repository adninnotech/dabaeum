package com.adn.dabaeum.credential.domain;

import java.util.UUID;

public interface CredentialUriProvider {

    String contextUrl();

    String vocabularyUrl();

    String issuerUrl(UUID institutionId);

    String keyId(UUID institutionId);

    /** 버전 행의 kid 조각으로 만든 kid (설계서 §6.2). 전역 {@link #keyId(UUID)} 는 전환 이전 발급분 전용이다. */
    default String keyId(UUID institutionId, String kidFragment) {
        if (kidFragment == null || !kidFragment.matches("^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$")) {
            throw new IllegalArgumentException("kidFragment format is invalid");
        }
        return issuerUrl(institutionId) + "#" + kidFragment;
    }

    /** 전환 이전 발급분의 credentialStatus.id. 그 VC 들을 계속 검증하기 위해 유지한다. */
    String statusUrl(String credentialNo);

    /** BitstringStatusListCredential 의 공개 URL. VC 의 statusListCredential 이 가리킨다. */
    String statusListUrl(UUID listId);

    /**
     * 이미 서명된 문서를 검증할 때 쓰는 주소 체계. 문서 안 URL(발급자·상태 주소)이 등록된 옛 공개 주소의 VC 경로로
     * 시작하면 그 주소 기준의 제공자를 돌려주고, 아니면 자신을 돌려준다. 새 문서를 만들거나 서명할 때는 쓰지 않는다.
     */
    default CredentialUriProvider forSignedUrl(String signedUrl) {
        return this;
    }
}
