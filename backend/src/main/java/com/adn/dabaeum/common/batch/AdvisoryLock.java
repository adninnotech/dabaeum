package com.adn.dabaeum.common.batch;

@FunctionalInterface
public interface AdvisoryLock {

    /** 현재 트랜잭션 동안 key 잠금을 시도한다. 다른 세션이 쥐고 있으면 기다리지 않고 false. 커밋·롤백 때 풀린다. */
    boolean tryLockForTransaction(long key);
}
