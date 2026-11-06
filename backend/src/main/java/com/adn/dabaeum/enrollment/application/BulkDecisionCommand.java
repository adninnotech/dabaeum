package com.adn.dabaeum.enrollment.application;

import java.util.List;
import java.util.UUID;

/** 목록 화면 체크박스로 고른 수강신청을 한 번에 승인하거나 반려한다. */
public record BulkDecisionCommand(
    List<UUID> enrollmentIds,
    Decision decision,
    String reason
) {

    public enum Decision { APPROVE, REJECT }
}
