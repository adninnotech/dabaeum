package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.course.domain.CourseFeeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 학습 목록 4탭(수강 중·관심·문의·후기)이 {@code @JsonUnwrapped} 로 펼쳐 쓰는 공통 카드 조각.
 * 강사명·회차수·교육비·태그처럼 목록 카드에 늘 같이 나오는 필드를 한 곳에 묶었다.
 */
public record CourseCardFields(
    String institutionName,
    String instructorName,
    String classificationCode,
    /** 표시용 분류 경로(1→3단계 이름). 분류가 없으면 빈 배열. */
    List<String> classificationPath,
    String educationType,
    Integer sessionCount,
    String tags,
    CourseFeeType tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId
) {
}
