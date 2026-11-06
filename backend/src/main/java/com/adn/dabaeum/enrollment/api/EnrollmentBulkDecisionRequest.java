package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.application.BulkDecisionCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 목록 화면의 체크박스로 고른 수강신청을 한 번에 처리한다.
 *
 * <p>한 번에 100건까지만 받는다 — 목록 한 페이지가 100건이라 그 이상은 화면에서 고를 수 없고,
 * 건별 트랜잭션으로 도는 요청이 무한정 길어지는 것도 막는다. {@code reason} 은 REJECT 일 때만
 * 필수이며 서비스가 검사한다.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record EnrollmentBulkDecisionRequest(
    @NotEmpty @Size(max = 100) List<UUID> enrollmentIds,
    @NotNull BulkDecisionCommand.Decision decision,
    @Size(max = 1000) String reason
) {
}
