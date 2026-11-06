package com.adn.dabaeum.accesslog.application;

import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.RetentionTask;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 2년 지난 관리자 접속기록을 지운다 (설계서 §10.2, §14). */
@Component
@Profile({"local", "dev"})
public class AdminAccessLogRetentionTask implements RetentionTask {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final int RETENTION_YEARS = 2;

    private final AdminAccessLogRepository repository;

    public AdminAccessLogRetentionTask(AdminAccessLogRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String name() {
        return "admin-access-logs";
    }

    @Override
    public long lockKey() {
        return AdvisoryLockKeys.ADMIN_ACCESS_LOG_CLEANUP;
    }

    /** 윤년을 달력으로 계산하려고 서울 날짜로 뺀다. */
    @Override
    public int purge(Instant now) {
        return repository.deleteCreatedBefore(now.atZone(SEOUL).minusYears(RETENTION_YEARS).toInstant());
    }
}
