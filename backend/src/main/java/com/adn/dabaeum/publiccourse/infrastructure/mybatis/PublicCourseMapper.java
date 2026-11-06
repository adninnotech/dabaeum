package com.adn.dabaeum.publiccourse.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 공공데이터 강좌 매퍼. 배열 컬럼은 {@code chr(31)} 로 이은 문자열로, 원본은 JSON 문자열로 주고받는다
 * (별도 타입 핸들러를 두지 않는다).
 */
@Mapper
public interface PublicCourseMapper {

    List<StoredRow> selectStoredByInsttCode(@Param("insttCode") String insttCode);

    int insert(CourseRow row);

    int replace(CourseRow row);

    int touch(@Param("sourceKeys") List<String> sourceKeys, @Param("seenAt") Instant seenAt);

    int deactivateUnseen(@Param("insttCode") String insttCode, @Param("seenAt") Instant seenAt);

    CourseRow selectById(@Param("id") UUID id);

    List<CourseRow> search(@Param("query") QueryParams query, @Param("limit") int limit, @Param("offset") long offset);

    long count(@Param("query") QueryParams query);

    List<RegionCountRow> countByRegion(@Param("query") QueryParams query);

    record RegionCountRow(String regionCode, long count) {
    }

    record StoredRow(String sourceKey, UUID id, String rawHash, boolean active) {
    }

    record CourseRow(
        UUID id,
        String sourceKey,
        String rawHash,
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
        String operDays,
        String targetTypes,
        String method,
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
        LocalDate referenceDate,
        String rawJson,
        boolean active,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant deactivatedAt
    ) {
    }

    /**
     * 검색 조건. {@code targetTypes}·{@code operDays} 는 chr(31) 로 이은 문자열이며 비어 있으면 null.
     * 시작 시각은 [{@code startTimeFrom}, {@code startTimeBefore}) 이고 null 이면 그쪽 끝은 열려 있다.
     * {@code receiptStatusOn} 이 있으면 {@code sortColumn} 대신 접수 상태 묶음으로 정렬한다.
     */
    record QueryParams(
        String keyword,
        String method,
        String targetTypes,
        String regionCode,
        Boolean free,
        LocalDate from,
        LocalDate to,
        LocalDate endedFrom,
        LocalDate receiptOpenOn,
        String operDays,
        LocalTime startTimeFrom,
        LocalTime startTimeBefore,
        String sortColumn,
        boolean ascending,
        LocalDate receiptStatusOn
    ) {
    }
}
