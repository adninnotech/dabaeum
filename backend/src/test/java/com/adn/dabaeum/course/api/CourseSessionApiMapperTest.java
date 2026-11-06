package com.adn.dabaeum.course.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.application.CreateCourseSessionBulkCommand;
import com.adn.dabaeum.course.application.CreateCourseSessionCommand;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseSessionApiMapperTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");

    private final CourseSessionApiMapper mapper = new CourseSessionApiMapper(
        Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void 반복규칙의_서울_14시는_UTC_05시로_저장된다() {
        CourseSessionBulkCreateRequest request = new CourseSessionBulkCreateRequest(
            new CourseSessionBulkCreateRequest.RepeatPayload(
                LocalDate.of(2026, 11, 5), Set.of(DayOfWeek.THURSDAY),
                LocalTime.of(14, 0), LocalTime.of(16, 0), 1),
            null, null);

        CreateCourseSessionBulkCommand command = mapper.toBulkCommand(COURSE_ID, request);

        assertThat(command.sessions()).hasSize(1);
        CreateCourseSessionCommand session = command.sessions().get(0);
        assertThat(session.startsAt()).isEqualTo(Instant.parse("2026-11-05T05:00:00Z"));
        assertThat(session.endsAt()).isEqualTo(Instant.parse("2026-11-05T07:00:00Z"));
    }

    @Test
    void repeat과_sessions를_같이_주면_거부한다() {
        CourseSessionBulkCreateRequest request = new CourseSessionBulkCreateRequest(
            new CourseSessionBulkCreateRequest.RepeatPayload(
                LocalDate.of(2026, 11, 5), Set.of(DayOfWeek.THURSDAY),
                LocalTime.of(14, 0), LocalTime.of(16, 0), 1),
            List.of(new CourseSessionCreateRequest()),
            null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> mapper.toBulkCommand(COURSE_ID, request))
            .isInstanceOf(com.adn.dabaeum.common.api.ApiException.class);
    }

    @Test
    void sessions에_null원소가_있으면_422로_거부한다() {
        CourseSessionBulkCreateRequest request = new CourseSessionBulkCreateRequest(
            null, java.util.Arrays.asList((CourseSessionCreateRequest) null), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> mapper.toBulkCommand(COURSE_ID, request))
            .isInstanceOf(com.adn.dabaeum.common.api.ApiException.class)
            .satisfies(exception -> assertThat(
                ((com.adn.dabaeum.common.api.ApiException) exception).status())
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @Test
    void 둘_다_없으면_거부한다() {
        CourseSessionBulkCreateRequest request =
            new CourseSessionBulkCreateRequest(null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> mapper.toBulkCommand(COURSE_ID, request))
            .isInstanceOf(com.adn.dabaeum.common.api.ApiException.class);
    }
}
