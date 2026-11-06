package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class CourseSessionCreateRequest {

    @NotNull @Min(1)
    private Integer sessionNo;
    private Instant startsAt;
    private Instant endsAt;
    @Size(max = 500)
    private String location;
    @Size(max = 200)
    private String topic;
    private Instant attendanceOpensAt;
    private Instant attendanceClosesAt;
    private CourseSessionStatus status = CourseSessionStatus.SCHEDULED;
    private boolean statusPresent;

    public CourseSessionCreateRequest() {
    }

    @JsonSetter("sessionNo") public void setSessionNo(Integer value) { sessionNo = value; }
    @JsonSetter("startsAt") public void setStartsAt(Instant value) { startsAt = value; }
    @JsonSetter("endsAt") public void setEndsAt(Instant value) { endsAt = value; }
    @JsonSetter("location") public void setLocation(String value) { location = value; }
    @JsonSetter("topic") public void setTopic(String value) { topic = value; }
    @JsonSetter("attendanceOpensAt") public void setAttendanceOpensAt(Instant value) { attendanceOpensAt = value; }
    @JsonSetter("attendanceClosesAt") public void setAttendanceClosesAt(Instant value) { attendanceClosesAt = value; }
    @JsonSetter("status") public void setStatus(CourseSessionStatus value) { statusPresent = true; status = value; }

    public Integer sessionNo() { return sessionNo; }
    public Instant startsAt() { return startsAt; }
    public Instant endsAt() { return endsAt; }
    public String location() { return location; }
    public String topic() { return topic; }
    public Instant attendanceOpensAt() { return attendanceOpensAt; }
    public Instant attendanceClosesAt() { return attendanceClosesAt; }
    public CourseSessionStatus status() { return status; }

    @JsonIgnore @AssertTrue(message = "startsAt must be before endsAt")
    public boolean isTimeRangeValid() {
        return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
    }

    @JsonIgnore @AssertTrue(message = "attendance window must be ordered")
    public boolean isAttendanceRangeValid() {
        return attendanceOpensAt == null || attendanceClosesAt == null
            || attendanceOpensAt.isBefore(attendanceClosesAt);
    }

    @JsonIgnore @AssertTrue(message = "status must not be null when provided")
    public boolean isStatusValid() { return !statusPresent || status != null; }
}
