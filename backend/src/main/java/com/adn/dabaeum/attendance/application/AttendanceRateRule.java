package com.adn.dabaeum.attendance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 출석률 규칙 한 곳. 학습자 출결 요약·강사 수강 현황·이수 평가가 같은 숫자를 보여야 한다(QA ATT-09·DATA-04).
 *
 * <p>출석률 = (출석 + 지각) / (센 회차 − 공결) × 100, 소수 둘째 자리 반올림. 분모가 0 이면 0.
 * 공결은 출석으로도 결석으로도 보지 않고 분모에서 뺀다.
 */
public final class AttendanceRateRule {

    private AttendanceRateRule() {
    }

    public static BigDecimal rate(int present, int late, int excused, int countedSessions) {
        int denominator = countedSessions - excused;
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf((long) present + late)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
