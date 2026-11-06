package com.adn.dabaeum.authentication.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 자체 로그인 실패 횟수와 잠금 (설계서 §10.3). 서버가 여러 대여도 같은 값을 보도록 tb_users 에 둔다. */
public interface LoginAttemptRepository {

    /** 잠금 만료 시각. 잠긴 적이 없으면 비어 있다. 이미 지난 시각일 수 있다 — 판정은 호출자가 한다. */
    Optional<Instant> findLockedUntil(UUID userId);

    /**
     * 실패 1회를 한 문장으로 더한다(동시 실패도 빠짐없이 센다). 이번 실패로 threshold 에 닿으면 횟수를 0 으로 되돌리고
     * lockUntil 까지 잠근 뒤 true.
     */
    boolean recordFailure(UUID userId, int threshold, Instant lockUntil);

    /** 성공 로그인·비밀번호 재설정·관리자 초기화가 실패 횟수와 잠금을 지운다. */
    void clear(UUID userId);
}
