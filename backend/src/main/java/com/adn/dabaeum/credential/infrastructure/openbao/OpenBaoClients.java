package com.adn.dabaeum.credential.infrastructure.openbao;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.core.VaultTemplate;

/**
 * 역할별 OpenBao 클라이언트. 발급 역할은 education-sign 의 sign 만, 상태 역할은 education-status 의
 * sign 만, 키 관리 역할은 두 mount 의 생성·회전·config 만 할 수 있다 (설계서 §13.3, §13.4).
 */
public record OpenBaoClients(
    VaultTemplate signing,
    VaultTemplate status,
    VaultTemplate keyAdmin,
    ThreadPoolTaskScheduler sessionScheduler
) implements AutoCloseable {

    /** 토큰 갱신 스케줄러를 내린다. 컨텍스트 종료 때 OpenBaoConfiguration 의 destroyMethod 가 부른다. */
    @Override
    public void close() {
        sessionScheduler.shutdown();
    }
}
