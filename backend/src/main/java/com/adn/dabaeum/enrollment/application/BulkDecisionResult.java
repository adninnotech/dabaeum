package com.adn.dabaeum.enrollment.application;

import java.util.List;
import java.util.UUID;

/**
 * 일괄 처리 결과. 부분 성공을 허용하므로 성공·실패가 한 응답에 같이 담긴다.
 *
 * <p>20건 중 1건이 이미 취소됐다고 나머지 19건을 되돌리면 관리자는 무엇이 문제인지 모른 채
 * 다시 눌러야 한다. 대신 실패한 건과 이유를 돌려준다.
 */
public record BulkDecisionResult(
    List<UUID> succeeded,
    List<Failure> failed
) {

    /** reason 은 오류 코드 이름과 메시지 — 프론트가 "이미 취소됨" 과 "정원 초과" 를 구분해야 한다. */
    public record Failure(UUID enrollmentId, String reason) {
    }
}
