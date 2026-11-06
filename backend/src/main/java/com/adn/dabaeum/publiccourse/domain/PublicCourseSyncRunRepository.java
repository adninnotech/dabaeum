package com.adn.dabaeum.publiccourse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 수집 실행 이력 저장소 (설계서 §4.2). */
public interface PublicCourseSyncRunRepository {

    void insert(PublicCourseSyncRun run);

    void update(PublicCourseSyncRun run);

    Optional<PublicCourseSyncRun> findById(UUID id);

    /** 최신 시작순. */
    List<PublicCourseSyncRun> findPage(int limit, long offset);

    long count();

    /**
     * 동시에 RUNNING 은 하나뿐이다(DB 유일 인덱스). 이미 있으면 {@code DuplicateKeyException}.
     * {@code startedBefore} 이전에 시작해 RUNNING 으로 남은 이력은 서버가 내려간 것으로 보고
     * FAILED(INTERRUPTED) 로 닫는다. 닫은 건수.
     */
    int closeStaleRunning(Instant startedBefore, Instant at);
}
