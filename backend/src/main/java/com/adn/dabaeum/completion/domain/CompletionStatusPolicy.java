package com.adn.dabaeum.completion.domain;

public class CompletionStatusPolicy {

    public boolean canEvaluate(CompletionStatus status) {
        return switch (status) {
            case PENDING_EVALUATION, ELIGIBLE, NOT_COMPLETED -> true;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** 관리자 정정: 확정된 이수를 평가 상태(ELIGIBLE)로 되돌린다. */
    public boolean canRevert(CompletionStatus status) {
        return status == CompletionStatus.COMPLETED;
    }

    public boolean canConfirm(CompletionStatus status) {
        return switch (status) {
            case ELIGIBLE -> true;
            case PENDING_EVALUATION, COMPLETED, NOT_COMPLETED, CANCELLED -> false;
        };
    }
}
