package com.adn.dabaeum.authentication.infrastructure.mybatis;

import com.adn.dabaeum.authentication.domain.PasswordResetToken;
import com.adn.dabaeum.authentication.domain.PasswordResetTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class PasswordResetTokenMyBatisRepository
    implements PasswordResetTokenRepository {

    private final PasswordResetTokenMapper mapper;

    public PasswordResetTokenMyBatisRepository(PasswordResetTokenMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(PasswordResetToken token) {
        mapper.insert(new PasswordResetTokenRow(
            token.id(), token.userId(), token.tokenHash(),
            token.expiresAt(), token.usedAt(), token.createdAt()));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(mapper.selectByTokenHash(tokenHash))
            .map(row -> new PasswordResetToken(
                row.id(), row.userId(), row.tokenHash(),
                row.expiresAt(), row.usedAt(), row.createdAt()));
    }

    @Override
    public boolean markUsed(UUID id, Instant usedAt) {
        return mapper.markUsed(id, usedAt) == 1;
    }
}
