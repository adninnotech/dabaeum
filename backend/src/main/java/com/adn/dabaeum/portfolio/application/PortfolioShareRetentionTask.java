package com.adn.dabaeum.portfolio.application;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.RetentionTask;
import com.adn.dabaeum.portfolio.domain.PortfolioRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 만료·폐기 후 90일 지난 공유 링크 삭제 (설계서 §14). */
@Component
@Profile({"local", "dev"})
public class PortfolioShareRetentionTask implements RetentionTask {

    static final Duration GRACE = Duration.ofDays(90);

    private final PortfolioRepository repository;

    public PortfolioShareRetentionTask(PortfolioRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "portfolio-shares";
    }

    @Override
    public long lockKey() {
        return AdvisoryLockKeys.PORTFOLIO_SHARE_CLEANUP;
    }

    @Override
    public int purge(Instant now) {
        return repository.deleteSharesEndedBefore(now.minus(GRACE));
    }
}
