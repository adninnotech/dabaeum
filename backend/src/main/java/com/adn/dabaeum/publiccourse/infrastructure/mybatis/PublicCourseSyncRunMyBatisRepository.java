package com.adn.dabaeum.publiccourse.infrastructure.mybatis;

import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.Counts;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.InstitutionResult;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile({"local", "dev"})
public class PublicCourseSyncRunMyBatisRepository implements PublicCourseSyncRunRepository {

    private final PublicCourseSyncRunMapper mapper;
    private final ObjectMapper objectMapper;

    public PublicCourseSyncRunMyBatisRepository(PublicCourseSyncRunMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(PublicCourseSyncRun run) {
        mapper.insert(toRow(run));
    }

    @Override
    public void update(PublicCourseSyncRun run) {
        if (mapper.update(toRow(run)) != 1) {
            throw new IllegalStateException("Public course sync run is missing");
        }
    }

    @Override
    public Optional<PublicCourseSyncRun> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<PublicCourseSyncRun> findPage(int limit, long offset) {
        return mapper.selectPage(limit, offset).stream().map(this::toDomain).toList();
    }

    @Override
    public long count() {
        return mapper.count();
    }

    @Override
    public int closeStaleRunning(Instant startedBefore, Instant at) {
        return mapper.closeStaleRunning(startedBefore, at);
    }

    private PublicCourseSyncRunMapper.RunRow toRow(PublicCourseSyncRun run) {
        Counts counts = run.counts();
        return new PublicCourseSyncRunMapper.RunRow(run.id(), run.trigger().name(), run.requestedBy(),
            run.status().name(), run.startedAt(), run.finishedAt(), counts.fetched(), counts.inserted(),
            counts.updated(), counts.unchanged(), counts.deactivated(), counts.skipped(), counts.parseFailures(),
            counts.keyCollisions(), objectMapper.writeValueAsString(run.institutionResults()), run.errorCode());
    }

    private PublicCourseSyncRun toDomain(PublicCourseSyncRunMapper.RunRow row) {
        Counts counts = new Counts(row.fetched(), row.inserted(), row.updated(), row.unchanged(), row.deactivated(),
            row.skipped(), row.parseFailures(), row.keyCollisions());
        List<InstitutionResult> results = Arrays.asList(
            objectMapper.readValue(row.institutionResults(), InstitutionResult[].class));
        return new PublicCourseSyncRun(row.id(), PublicCourseSyncRun.Trigger.valueOf(row.triggerType()),
            row.requestedBy(), PublicCourseSyncRun.Status.valueOf(row.status()), row.startedAt(), row.finishedAt(),
            counts, results, row.errorCode());
    }
}
