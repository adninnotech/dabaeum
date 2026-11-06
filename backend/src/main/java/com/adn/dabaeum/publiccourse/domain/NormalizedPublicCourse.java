package com.adn.dabaeum.publiccourse.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 공공데이터 강좌 원본 행을 해석한 값 (설계서 §3.2). 해석에 실패한 값은 null 이다.
 * 목록 필드는 비어 있을 수 있지만 null 이 아니다.
 */
public record NormalizedPublicCourse(
    String insttCode,
    String insttName,
    String lectureName,
    String instructorName,
    String lectureContent,
    String place,
    String roadAddress,
    String operInstitutionName,
    String operPhone,
    String homepageUrl,
    LocalDate edcStartDate,
    LocalDate edcEndDate,
    LocalTime startTime,
    LocalTime endTime,
    List<String> operDays,
    List<String> targetTypes,
    PublicCourseMethod method,
    String regionCode,
    Integer capacity,
    Integer costAmount,
    Boolean free,
    LocalDate receiptStartDate,
    LocalDate receiptEndDate,
    String receiptMethod,
    String selectionMethod,
    Boolean trainingSubsidy,
    Boolean creditBank,
    Boolean learningAccount,
    LocalDate referenceDate
) {

    public NormalizedPublicCourse {
        operDays = List.copyOf(operDays);
        targetTypes = List.copyOf(targetTypes);
    }
}
