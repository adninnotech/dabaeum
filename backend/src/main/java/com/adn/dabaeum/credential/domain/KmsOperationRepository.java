package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 작업 기록 저장소 (설계서 §11.4). 소유권은 조건부 UPDATE 의 반환 행 수로만 판단한다. */
public interface KmsOperationRepository {

    /**
     * @throws KmsOperationConflictException 같은 키의 진행 중 작업이 있거나 멱등키가 겹치면
     */
    void insert(KmsOperation operation);

    Optional<KmsOperation> findById(UUID id);

    /** 진행 중(PREPARED·UNCONFIRMED·RECORDED) 작업. 부분 유니크라 최대 하나다. */
    Optional<KmsOperation> findInProgressBySignKeyId(UUID signKeyId);

    /** created_at 내림차순, 최대 limit 건. 관리자 화면의 작업 이력 (§14). */
    List<KmsOperation> findByInstitutionId(UUID institutionId, int limit);

    /** 진행 중(PREPARED·UNCONFIRMED·RECORDED) 작업 전부를 prepared_at 오름차순으로 최대 limit 건. 복구 워커가 쓴다 (§15.1). */
    List<KmsOperation> findInProgress(int limit);

    /**
     * 소유권 획득: 비어 있거나 만료된 경우에만.
     *
     * @return 1행 갱신이면 true (내 것)
     */
    boolean acquireOwnership(UUID id, String owner, Instant now, Instant leaseUntil);

    /**
     * 외부 호출 직전 재확인. lease 를 연장하고 {@code last_call_at} 을 남긴다.
     *
     * @return 0행이면 false — 즉시 중단 (다른 스레드가 회수했거나 상태가 바뀜)
     */
    boolean reconfirmOwnership(UUID id, String owner, KmsOperationStatus expectedStatus, Instant now,
        Instant leaseUntil);

    /**
     * 상태·관측 버전·값·사유·updated_at 만 갱신한다. owner·lease_until 은 바꾸지 않는다.
     *
     * @return 1행이면 true. 0행은 작업 기록이 사라졌거나 다른 곳에서 지워진 것이므로 호출부가 경고를 남긴다
     */
    boolean update(KmsOperation operation);

    /** @return 1행이면 true */
    boolean releaseOwnership(UUID id, String owner);
}
