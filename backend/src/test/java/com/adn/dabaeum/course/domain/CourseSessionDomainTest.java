package com.adn.dabaeum.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseSessionDomainTest {

    private static final UUID SESSION_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant STARTS = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-01T02:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void acceptsValidSessionAndNullableAttendanceFields() {
        CourseSession session = new CourseSession(
            SESSION_ID,
            COURSE_ID,
            1,
            STARTS,
            ENDS,
            null,
            null,
            null,
            CourseSessionStatus.SCHEDULED,
            CREATED,
            CREATED,
            null
        );

        assertThat(session.sessionNo()).isEqualTo(1);
        assertThat(session.status()).isEqualTo(CourseSessionStatus.SCHEDULED);
        assertThat(session.location()).isNull();
    }

    @Test
    void rejectsInvalidIdentityAndTimeBoundaries() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CourseSession(
            SESSION_ID, COURSE_ID, 0, STARTS, ENDS, null, null, null,
            CourseSessionStatus.SCHEDULED, CREATED, CREATED, null
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new CourseSession(
            SESSION_ID, COURSE_ID, 1, ENDS, STARTS, null, null, null,
            CourseSessionStatus.SCHEDULED, CREATED, CREATED, null
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new CourseSession(
            SESSION_ID, COURSE_ID, 1, STARTS, ENDS, null,
            ENDS, STARTS, CourseSessionStatus.SCHEDULED, CREATED, CREATED, null
        ));
    }

    @Test
    void acceptsOpenAttendanceWindowOnlyWhenOrdered() {
        CourseSession session = new CourseSession(
            SESSION_ID, COURSE_ID, 1, STARTS, ENDS, "서울 교육장",
            STARTS.plusSeconds(300), ENDS.minusSeconds(300),
            CourseSessionStatus.OPEN, CREATED, CREATED, null
        );

        assertThat(session.attendanceOpensAt()).isBefore(session.attendanceClosesAt());
    }

    @Test
    void enforcesSessionStatusTransitions() {
        assertThat(CourseSessionStatusPolicy.updateTarget(
            CourseSessionStatus.SCHEDULED, CourseSessionStatus.OPEN
        )).isEqualTo(CourseSessionStatus.OPEN);
        assertThat(CourseSessionStatusPolicy.updateTarget(
            CourseSessionStatus.OPEN, CourseSessionStatus.COMPLETED
        )).isEqualTo(CourseSessionStatus.COMPLETED);
        assertThat(CourseSessionStatusPolicy.updateTarget(
            CourseSessionStatus.SCHEDULED, CourseSessionStatus.CANCELLED
        )).isEqualTo(CourseSessionStatus.CANCELLED);
        assertThatThrownBy(() -> CourseSessionStatusPolicy.updateTarget(
            CourseSessionStatus.COMPLETED, CourseSessionStatus.OPEN
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CourseSessionStatusPolicy.updateTarget(
            CourseSessionStatus.OPEN, CourseSessionStatus.SCHEDULED
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 온라인_회차는_날짜없이_주제만으로_만들_수_있다() {
        CourseSession session = new CourseSession(
            SESSION_ID, COURSE_ID, 1, null, null, null, null, null,
            CourseSessionStatus.SCHEDULED, CREATED, CREATED, "1강. 데이터란 무엇인가"
        );

        assertThat(session.startsAt()).isNull();
        assertThat(session.endsAt()).isNull();
        assertThat(session.topic()).isEqualTo("1강. 데이터란 무엇인가");
    }

    @Test
    void 시작만_있고_종료가_없으면_거부한다() {
        assertThatThrownBy(() -> new CourseSession(
            SESSION_ID, COURSE_ID, 1, STARTS, null, null, null, null,
            CourseSessionStatus.SCHEDULED, CREATED, CREATED, null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endsAt");
    }

    @Test
    void 주제는_200자를_넘을_수_없다() {
        assertThatThrownBy(() -> new CourseSession(
            SESSION_ID, COURSE_ID, 1, null, null, null, null, null,
            CourseSessionStatus.SCHEDULED, CREATED, CREATED, "가".repeat(201)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("topic");
    }
}
