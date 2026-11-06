package com.adn.dabaeum.common.infrastructure.mybatis;

import com.adn.dabaeum.common.batch.AdvisoryLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
public class MyBatisAdvisoryLock implements AdvisoryLock {

    private final AdvisoryLockMapper mapper;

    public MyBatisAdvisoryLock(AdvisoryLockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean tryLockForTransaction(long key) {
        return mapper.tryXactLock(key);
    }
}
