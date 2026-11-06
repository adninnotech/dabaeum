package com.adn.dabaeum.portfolio.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository {

    Optional<PortfolioRecords.Owner> owner(UUID userId);

    /** COMPLETED 수료, 최신순. credentialId 는 그 수료의 ISSUED 최신 수료증(없으면 null). */
    List<PortfolioRecords.CompletionItem> completions(UUID userId);

    /** 수료 확정 강좌의 회차 시간 합(분). 취소된 회차는 뺀다. */
    long learningMinutes(UUID userId);

    /** ISSUED 수료증, 발급 최신순. */
    List<PortfolioRecords.CredentialItem> credentials(UUID userId);

    /** ISSUED 배지, 발급 최신순. */
    List<PortfolioRecords.BadgeItem> badges(UUID userId);

    void saveShare(PortfolioRecords.Share share, String tokenHash);

    List<PortfolioRecords.Share> shares(UUID userId);

    boolean revokeShare(UUID shareId, UUID userId, Instant at);

    /** 살아 있는 공유를 찾아 조회 수를 올리고 돌려준다. 만료·폐기·없음은 빈 값. */
    Optional<PortfolioRecords.Share> openShare(String tokenHash, Instant at);

    /** 만료 또는 폐기 시각이 threshold 이전인 공유 삭제(설계서 §14, 90일). */
    int deleteSharesEndedBefore(Instant threshold);
}
