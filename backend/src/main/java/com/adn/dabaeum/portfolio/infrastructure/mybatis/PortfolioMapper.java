package com.adn.dabaeum.portfolio.infrastructure.mybatis;

import com.adn.dabaeum.portfolio.domain.PortfolioRecords;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PortfolioMapper {

    PortfolioRecords.Owner selectOwner(@Param("userId") UUID userId);

    List<PortfolioRecords.CompletionItem> selectCompletions(@Param("userId") UUID userId);

    long selectLearningMinutes(@Param("userId") UUID userId);

    List<PortfolioRecords.CredentialItem> selectCredentials(@Param("userId") UUID userId);

    List<PortfolioRecords.BadgeItem> selectBadges(@Param("userId") UUID userId);

    int insertShare(
        @Param("id") UUID id, @Param("userId") UUID userId, @Param("tokenHash") String tokenHash,
        @Param("credentialIds") String[] credentialIds, @Param("badgeIds") String[] badgeIds,
        @Param("expiresAt") Instant expiresAt, @Param("createdAt") Instant createdAt
    );

    List<PortfolioShareRow> selectShares(@Param("userId") UUID userId);

    int revokeShare(@Param("id") UUID id, @Param("userId") UUID userId, @Param("at") Instant at);

    PortfolioShareRow openShare(@Param("tokenHash") String tokenHash, @Param("at") Instant at);

    int deleteSharesEndedBefore(@Param("threshold") Instant threshold);
}
