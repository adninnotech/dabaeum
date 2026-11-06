package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.math.BigDecimal;

/** 평균 진도율은 SQL 에서 소수로 뽑고 반올림은 리포지토리가 한다(Math.round). */
public record LearningActivityRow(
    Long totalStudyMinutes,
    BigDecimal averageProgressPercent,
    Long inquiryCount
) {
}
