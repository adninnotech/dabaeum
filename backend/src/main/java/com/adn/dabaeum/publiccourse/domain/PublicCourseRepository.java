package com.adn.dabaeum.publiccourse.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 공공데이터 강좌 저장소 (설계서 §3.4, §4.1, §5). 행은 지우지 않는다. */
public interface PublicCourseRepository {

    /** 한 기관의 저장된 강좌를 원본 키 → (id, 원본 해시, 활성) 으로. */
    Map<String, Stored> findStoredByInsttCode(String insttCode);

    void insert(PublicCourse course);

    /** 원본이 바뀐 강좌의 정규화 값·원본을 갱신하고 활성으로 되돌린다. */
    void replace(PublicCourse course);

    /** 원본이 그대로인 강좌의 마지막 확인 시각을 갱신하고 활성으로 되돌린다. 바뀐 행 수. */
    int touch(List<String> sourceKeys, Instant seenAt);

    /** 그 기관의 활성 강좌 중 {@code seenAt} 에 확인되지 않은 것을 비활성으로. 바뀐 행 수. */
    int deactivateUnseen(String insttCode, Instant seenAt);

    Optional<PublicCourse> findById(UUID id);

    List<PublicCourse> search(Query query, int limit, long offset);

    long count(Query query);

    /** 같은 조건의 구·군 코드별 건수. 구·군을 모르는 강좌는 빠진다. */
    Map<String, Long> countByRegion(Query query);

    record Stored(UUID id, String rawHash, boolean active) {
    }

    /** {@code RECEIPT_STATUS} 는 접수 중 → 예정 → 모름 → 마감 묶음 순이며 오름차순만 있다(설계서 §5.1). */
    enum SortField { EDC_START_DATE, RECEIPT_END_DATE, LECTURE_NAME, RECEIPT_STATUS }

    /**
     * 공개 목록 조건 (설계서 §5.1). {@code keyword} 는 {@code KeywordQuery} 로 이스케이프된 소문자다.
     * {@code endedFrom} 이 있으면 교육 종료일이 그날 이후(또는 미상)인 강좌만.
     * {@code receiptOpenOn} 이 있으면 그날 접수 중인 강좌만(마감일이 그날 이후, 시작일이 그날 이전 또는 미상).
     * {@code operDays} 는 그중 하나라도 운영하는 강좌, {@code timeOfDay} 는 시작 시각 구간이다.
     * {@code receiptStatusOn} 은 접수 상태 정렬의 기준일이며 그 정렬일 때만 있다.
     */
    record Query(
        String keyword,
        PublicCourseMethod method,
        List<String> targetTypes,
        String regionCode,
        Boolean free,
        LocalDate from,
        LocalDate to,
        LocalDate endedFrom,
        LocalDate receiptOpenOn,
        List<String> operDays,
        PublicCourseTimeOfDay timeOfDay,
        SortField sortField,
        boolean ascending,
        LocalDate receiptStatusOn
    ) {

        public Query {
            targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
            operDays = operDays == null ? List.of() : List.copyOf(operDays);
            sortField = sortField == null ? SortField.EDC_START_DATE : sortField;
            if ((sortField == SortField.RECEIPT_STATUS) != (receiptStatusOn != null)) {
                throw new IllegalArgumentException("receiptStatusOn is required only for RECEIPT_STATUS sort");
            }
            if (sortField == SortField.RECEIPT_STATUS && !ascending) {
                throw new IllegalArgumentException("RECEIPT_STATUS sort is ascending only");
            }
        }

        /** 구·군 조건만 뺀 새 조건. 구·군별 건수는 자기 조건을 걸지 않고 센다. */
        public Query withoutRegion() {
            return new Query(keyword, method, targetTypes, null, free, from, to, endedFrom, receiptOpenOn, operDays,
                timeOfDay, sortField, ascending, receiptStatusOn);
        }
    }
}
