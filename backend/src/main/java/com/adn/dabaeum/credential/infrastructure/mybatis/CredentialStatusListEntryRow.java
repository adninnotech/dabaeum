package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.UUID;

/** tb_credentials 의 상태 리스트 컬럼 두 개. 전환 이전 발급분은 둘 다 NULL 이다. */
public record CredentialStatusListEntryRow(
    UUID statusListId,
    Integer statusListIndex
) {
}
