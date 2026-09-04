package com.adn.dabaeum.completion.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CompletionEvaluationRequest(
    // 출석률·이수시간·학점은 서버가 출결 기록과 과정 정책으로 계산한다. 보낸 값은 참고용이며
    // 서버 계산값과 달라도 거부하지 않는다. 판정에 쓰인 값은 응답에 담긴다.
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    BigDecimal attendanceRate,
    @jakarta.validation.constraints.PositiveOrZero
    Integer completedMinutes,
    @DecimalMin("0.01")
    @DecimalMax("999.99")
    BigDecimal creditValue,
    @Size(max = 1000)
    String failureReason
) {
}
