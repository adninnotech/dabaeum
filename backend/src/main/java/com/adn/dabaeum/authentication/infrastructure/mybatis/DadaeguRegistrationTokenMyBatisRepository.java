package com.adn.dabaeum.authentication.infrastructure.mybatis;

import com.adn.dabaeum.authentication.domain.DadaeguRegistrationToken;
import com.adn.dabaeum.authentication.domain.DadaeguRegistrationTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class DadaeguRegistrationTokenMyBatisRepository
    implements DadaeguRegistrationTokenRepository {

    private final DadaeguRegistrationTokenMapper mapper;

    public DadaeguRegistrationTokenMyBatisRepository(DadaeguRegistrationTokenMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(DadaeguRegistrationToken token) {
        mapper.insert(new DadaeguRegistrationTokenRow(
            token.id(), token.tokenHash(), token.did(), token.name(), token.phone(),
            token.birthDate(), token.expiresAt(), token.usedAt(), token.createdAt()));
    }

    @Override
    public Optional<DadaeguRegistrationToken> consume(String tokenHash, Instant now) {
        return Optional.ofNullable(mapper.consume(tokenHash, now))
            .map(row -> new DadaeguRegistrationToken(
                row.id(), row.tokenHash(), row.did(), row.name(), row.phone(),
                row.birthDate(), row.expiresAt(), row.usedAt(), row.createdAt()));
    }

    @Override
    public int deleteExpiredBefore(Instant threshold) {
        return mapper.deleteExpiredBefore(threshold);
    }
}
