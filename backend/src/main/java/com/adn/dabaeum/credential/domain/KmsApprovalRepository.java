package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 승인 기록 저장소 (설계서 §11.5). 소비는 조건부 UPDATE 한 문장이며 반환 행 수로만 판단한다 (§12.2). */
public interface KmsApprovalRepository {

    void insert(KmsApproval approval);

    Optional<KmsApproval> findById(UUID id);

    /**
     * 같은 트랜잭션에서 작업 행을 먼저 INSERT 한 뒤 부른다 ({@code consumed_by_operation_id} FK).
     *
     * @return 정확히 1행을 갱신했으면 true. 아니면 호출자가 롤백하고 거부한다
     */
    boolean consume(KmsApprovalConsumption consumption);

    /** 미사용·미만료 승인. created_at 내림차순 (§14 "미사용 승인"). */
    List<KmsApproval> findOpenByInstitutionId(UUID institutionId, Instant now);
}
