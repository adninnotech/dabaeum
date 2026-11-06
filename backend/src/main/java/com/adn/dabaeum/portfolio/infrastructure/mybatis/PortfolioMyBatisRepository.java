package com.adn.dabaeum.portfolio.infrastructure.mybatis;

import com.adn.dabaeum.portfolio.domain.PortfolioRecords;
import com.adn.dabaeum.portfolio.domain.PortfolioRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class PortfolioMyBatisRepository implements PortfolioRepository {

    private final PortfolioMapper mapper;

    public PortfolioMyBatisRepository(PortfolioMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PortfolioRecords.Owner> owner(UUID userId) {
        return Optional.ofNullable(mapper.selectOwner(userId));
    }

    @Override
    public List<PortfolioRecords.CompletionItem> completions(UUID userId) {
        return mapper.selectCompletions(userId);
    }

    @Override
    public long learningMinutes(UUID userId) {
        return mapper.selectLearningMinutes(userId);
    }

    @Override
    public List<PortfolioRecords.CredentialItem> credentials(UUID userId) {
        return mapper.selectCredentials(userId);
    }

    @Override
    public List<PortfolioRecords.BadgeItem> badges(UUID userId) {
        return mapper.selectBadges(userId);
    }

    @Override
    public void saveShare(PortfolioRecords.Share share, String tokenHash) {
        mapper.insertShare(share.id(), share.userId(), tokenHash, strings(share.credentialIds()),
            strings(share.badgeIds()), share.expiresAt(), share.createdAt());
    }

    @Override
    public List<PortfolioRecords.Share> shares(UUID userId) {
        return mapper.selectShares(userId).stream().map(PortfolioMyBatisRepository::toShare).toList();
    }

    @Override
    public boolean revokeShare(UUID shareId, UUID userId, Instant at) {
        return mapper.revokeShare(shareId, userId, at) == 1;
    }

    @Override
    public Optional<PortfolioRecords.Share> openShare(String tokenHash, Instant at) {
        return Optional.ofNullable(mapper.openShare(tokenHash, at)).map(PortfolioMyBatisRepository::toShare);
    }

    @Override
    public int deleteSharesEndedBefore(Instant threshold) {
        return mapper.deleteSharesEndedBefore(threshold);
    }

    private static String[] strings(List<UUID> ids) {
        return ids.stream().map(UUID::toString).toArray(String[]::new);
    }

    private static List<UUID> uuids(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(UUID::fromString).toList();
    }

    private static PortfolioRecords.Share toShare(PortfolioShareRow row) {
        return new PortfolioRecords.Share(row.id(), row.userId(), uuids(row.credentialIdsCsv()), uuids(row.badgeIdsCsv()),
            row.expiresAt(), row.revokedAt(), row.viewCount(), row.lastViewedAt(), row.createdAt());
    }
}
