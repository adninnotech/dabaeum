package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CredentialSigningMyBatisRepository implements CredentialSigningRepository {

    private final CredentialSigningMapper mapper;

    public CredentialSigningMyBatisRepository(CredentialSigningMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CredentialSigningState> findSigningState(UUID credentialId) {
        return Optional.ofNullable(mapper.selectSigningState(credentialId))
            .map(row -> new CredentialSigningState(row.id(), CredentialStatus.valueOf(row.status()),
                row.signKeyId(), row.signKeyVersion(), row.signAttemptNo(), row.vcJwsHeader(),
                row.vcJwsPayload(), row.vcPayload()));
    }

    @Override
    public Optional<UUID> findInstitutionId(UUID credentialId) {
        return Optional.ofNullable(mapper.selectInstitutionId(credentialId)).map(UUID::fromString);
    }

    @Override
    public boolean commitSigningInput(
        UUID credentialId, UUID signKeyId, int signKeyVersion, CredentialSigningInput input,
        String issuerIdentifier, String subjectIdentifier, Instant issuedAt, Instant now
    ) {
        return mapper.commitSigningInput(credentialId, signKeyId, signKeyVersion, input.encodedHeader(),
            input.encodedPayload(), issuerIdentifier, subjectIdentifier, issuedAt, now) == 1;
    }

    @Override
    public boolean recommitHeader(
        UUID credentialId, int expectedAttemptNo, UUID signKeyId, int signKeyVersion,
        CredentialSigningInput input, Instant now
    ) {
        return mapper.recommitHeader(credentialId, expectedAttemptNo, signKeyId, signKeyVersion,
            input.encodedHeader(), input.encodedPayload(), now) == 1;
    }

    @Override
    public boolean storeSignature(
        UUID credentialId, int attemptNo, UUID signKeyId, int signKeyVersion,
        CredentialSigningInput input, String vcPayload, String vcHash, Instant now
    ) {
        return mapper.storeSignature(credentialId, attemptNo, signKeyId, signKeyVersion,
            input.encodedHeader(), input.encodedPayload(), vcPayload, vcHash, now) == 1;
    }
}
