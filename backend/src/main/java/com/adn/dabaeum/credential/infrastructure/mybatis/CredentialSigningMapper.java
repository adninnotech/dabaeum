package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CredentialSigningMapper {

    CredentialSigningStateRow selectSigningState(@Param("credentialId") UUID credentialId);

    String selectInstitutionId(@Param("credentialId") UUID credentialId);

    int commitSigningInput(
        @Param("credentialId") UUID credentialId,
        @Param("signKeyId") UUID signKeyId,
        @Param("signKeyVersion") int signKeyVersion,
        @Param("encodedHeader") String encodedHeader,
        @Param("encodedPayload") String encodedPayload,
        @Param("issuerIdentifier") String issuerIdentifier,
        @Param("subjectIdentifier") String subjectIdentifier,
        @Param("issuedAt") Instant issuedAt,
        @Param("now") Instant now
    );

    int recommitHeader(
        @Param("credentialId") UUID credentialId,
        @Param("expectedAttemptNo") int expectedAttemptNo,
        @Param("signKeyId") UUID signKeyId,
        @Param("signKeyVersion") int signKeyVersion,
        @Param("encodedHeader") String encodedHeader,
        @Param("encodedPayload") String encodedPayload,
        @Param("now") Instant now
    );

    int storeSignature(
        @Param("credentialId") UUID credentialId,
        @Param("attemptNo") int attemptNo,
        @Param("signKeyId") UUID signKeyId,
        @Param("signKeyVersion") int signKeyVersion,
        @Param("encodedHeader") String encodedHeader,
        @Param("encodedPayload") String encodedPayload,
        @Param("vcPayload") String vcPayload,
        @Param("vcHash") String vcHash,
        @Param("now") Instant now
    );
}
