package com.adn.dabaeum.common.batch;

import java.time.Instant;

/** 보존 기간이 지난 행을 지우는 작업 하나 (설계서 §14). 빈으로 등록하면 {@link RetentionCleanupScheduler} 가 돌린다. */
public interface RetentionTask {

    /** 로그용 이름. 개인정보를 넣지 않는다. */
    String name();

    /** {@link AdvisoryLockKeys} 의 상수. */
    long lockKey();

    /** now 기준 보존 기간이 지난 행을 지우고 지운 행 수를 돌려준다. */
    int purge(Instant now);
}
