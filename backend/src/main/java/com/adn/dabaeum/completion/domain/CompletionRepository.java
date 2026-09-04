package com.adn.dabaeum.completion.domain;

import java.util.Optional;
import java.util.UUID;

public interface CompletionRepository {

    void save(Completion completion);

    Optional<Completion> findById(UUID completionId);

    Optional<Completion> findByIdForUpdate(UUID completionId);

    Optional<Completion> findByEnrollmentId(UUID enrollmentId);

    Optional<Completion> findByEnrollmentIdForUpdate(UUID enrollmentId);

    boolean updateEvaluation(Completion completion, CompletionStatus expectedStatus);

    boolean confirm(Completion completion, CompletionStatus expectedStatus);

    /** 관리자 정정으로 확정을 취소한다. 기대 상태가 아니면 false. */
    boolean revertConfirmation(Completion completion, CompletionStatus expectedStatus);
}
