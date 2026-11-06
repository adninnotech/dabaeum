package com.adn.dabaeum.accesslog.infrastructure.mybatis;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLog;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogCriteria;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogEntry;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogPage;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class AdminAccessLogMyBatisRepository implements AdminAccessLogRepository {

    private final AdminAccessLogMapper mapper;

    public AdminAccessLogMyBatisRepository(AdminAccessLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AdminAccessLog log) {
        mapper.insert(new AdminAccessLogRow(
            log.id(), log.actorId(), log.actorRole(), log.action().name(), log.endpoint(),
            log.targetUserIds() == null
                ? null
                : log.targetUserIds().stream().map(UUID::toString).toArray(String[]::new),
            log.resultCount(), log.statusCode(), log.ipAddress(), log.userAgent(),
            log.requestId(), log.createdAt()));
    }

    @Override
    public int deleteCreatedBefore(Instant threshold) {
        return mapper.deleteCreatedBefore(threshold);
    }

    @Override
    public AdminAccessLogPage search(AdminAccessLogCriteria criteria) {
        List<AdminAccessLogEntry> entries = mapper.search(criteria).stream().map(this::toEntry).toList();
        return new AdminAccessLogPage(entries, mapper.count(criteria));
    }

    private AdminAccessLogEntry toEntry(AdminAccessLogEntryRow row) {
        List<UUID> targets = row.targetUserIdsCsv() == null
            ? null
            : Arrays.stream(row.targetUserIdsCsv().split(",")).map(UUID::fromString).toList();
        return new AdminAccessLogEntry(new AdminAccessLog(
            row.id(), row.actorId(), row.actorRole(), AccessLogAction.valueOf(row.action()), row.endpoint(),
            targets, row.resultCount(), row.statusCode(), row.ipAddress(), row.userAgent(),
            row.requestId(), row.createdAt()), row.actorName());
    }
}
