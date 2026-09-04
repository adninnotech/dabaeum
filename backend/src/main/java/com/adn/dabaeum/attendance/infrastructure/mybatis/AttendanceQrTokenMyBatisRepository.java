package com.adn.dabaeum.attendance.infrastructure.mybatis;

import com.adn.dabaeum.attendance.domain.AttendanceQrToken;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class AttendanceQrTokenMyBatisRepository implements AttendanceQrTokenRepository {

    private final AttendanceQrTokenMapper mapper;

    public AttendanceQrTokenMyBatisRepository(AttendanceQrTokenMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void revokeActiveBySession(UUID sessionId, Instant revokedAt) {
        mapper.revokeActiveBySession(sessionId, revokedAt);
    }

    @Override
    public void save(AttendanceQrToken token) {
        mapper.insert(toRow(token));
    }

    @Override
    public Optional<AttendanceQrToken> findByHash(String tokenHash) {
        return Optional.ofNullable(mapper.selectByHash(tokenHash)).map(this::toDomain);
    }

    private AttendanceQrTokenRow toRow(AttendanceQrToken token) {
        return new AttendanceQrTokenRow(
            token.id(),
            token.sessionId(),
            token.tokenHash(),
            token.issuedBy(),
            token.issuedAt(),
            token.expiresAt(),
            token.revokedAt(),
            token.createdAt()
        );
    }

    private AttendanceQrToken toDomain(AttendanceQrTokenRow row) {
        return new AttendanceQrToken(
            row.id(),
            row.sessionId(),
            row.tokenHash(),
            row.issuedBy(),
            row.issuedAt(),
            row.expiresAt(),
            row.revokedAt(),
            row.createdAt()
        );
    }
}
