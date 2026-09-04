package com.adn.dabaeum.fabric.application;

import java.time.Instant;

/** 작업자 응답이 유실된 경우 Fabric을 조회해 데이터베이스 상태 처리를 완료한다. */
public interface CredentialFabricReconciler {
    int reconcileBatch(int limit, Instant now);
}
