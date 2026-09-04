package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

/** VC 한 장에 영구 배정된 상태 리스트 위치. VC 본문의 credentialStatus 가 이 값을 가리킨다. */
public record CredentialStatusListEntry(UUID listId, int index) {

    public CredentialStatusListEntry {
        Objects.requireNonNull(listId, "listId");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
    }
}
