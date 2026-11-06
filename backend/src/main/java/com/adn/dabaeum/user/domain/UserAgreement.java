package com.adn.dabaeum.user.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 약관 동의 이력 한 건. append-only — 철회·재동의도 새 행이며, 기존 행을 갱신하지 않는다.
 * 개인정보 관련 증빙이라 언제 무엇에 동의(또는 거부)했는지가 지워지면 안 된다.
 */
public record UserAgreement(
    UUID id,
    UUID userId,
    AgreementType agreementType,
    boolean agreed,
    Instant agreedAt
) {
}
