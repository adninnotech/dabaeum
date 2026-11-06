package com.adn.dabaeum.course.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * D-2 회차 일괄 생성 요청. {@code repeat}(반복 규칙) 또는 {@code sessions}(직접 입력) 중
 * 정확히 하나만 채운다 — 검증은 {@code CourseSessionApiMapper} 에서 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CourseSessionBulkCreateRequest(
    @Valid RepeatPayload repeat,
    List<@Valid CourseSessionCreateRequest> sessions,
    /** true 면 기존 회차를 전부 지우고 새로 만든다. 위저드 재저장용. */
    Boolean replaceExisting
) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RepeatPayload(
        @NotNull LocalDate firstDate,
        @NotEmpty Set<DayOfWeek> daysOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(1) @Max(200) Integer count
    ) {
    }
}
