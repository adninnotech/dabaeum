package com.adn.dabaeum.common.batch;

import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 잠금을 잡은 트랜잭션 안에서만 작업을 돌린다. 잠금과 작업이 같은 트랜잭션이라 작업이 끝나면(커밋·롤백) 잠금도 풀린다.
 * 세션 잠금(pg_try_advisory_lock)을 쓰지 않는 이유는 해제를 빠뜨리면 풀의 커넥션이 잠금을 쥔 채 남기 때문이다.
 */
public final class SingleInstanceJobRunner {

    private final AdvisoryLock lock;
    private final TransactionTemplate transactions;

    public SingleInstanceJobRunner(AdvisoryLock lock, PlatformTransactionManager transactionManager) {
        this.lock = Objects.requireNonNull(lock, "lock");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    /** 잠금을 얻어 job 을 돌렸으면 true, 다른 서버가 돌리는 중이라 건너뛰었으면 false. job 예외는 롤백 후 그대로 던진다. */
    public boolean runExclusively(long key, Runnable job) {
        Objects.requireNonNull(job, "job");
        Boolean ran = transactions.execute(status -> {
            if (!lock.tryLockForTransaction(key)) {
                return false;
            }
            job.run();
            return true;
        });
        return Boolean.TRUE.equals(ran);
    }
}
