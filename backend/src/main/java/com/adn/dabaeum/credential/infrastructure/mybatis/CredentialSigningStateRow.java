package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.UUID;

public record CredentialSigningStateRow(
    UUID id,
    String status,
    UUID signKeyId,
    Integer signKeyVersion,
    Integer signAttemptNo,
    String vcJwsHeader,
    String vcJwsPayload,
    String vcPayload
) {
}
