package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.application.CourseSessionUpdateField;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class CourseSessionUpdateRequest {

    @Min(1) private Integer sessionNo;
    private Instant startsAt;
    private Instant endsAt;
    @Size(max = 500) private String location;
    private Instant attendanceOpensAt;
    private Instant attendanceClosesAt;
    private CourseSessionStatus status;
    private boolean sessionNoPresent;
    private boolean startsAtPresent;
    private boolean endsAtPresent;
    private boolean locationPresent;
    private boolean attendanceOpensAtPresent;
    private boolean attendanceClosesAtPresent;
    private boolean statusPresent;

    public CourseSessionUpdateRequest() {
    }

    @JsonSetter("sessionNo") public void setSessionNo(Integer value) { sessionNoPresent = true; sessionNo = value; }
    @JsonSetter("startsAt") public void setStartsAt(Instant value) { startsAtPresent = true; startsAt = value; }
    @JsonSetter("endsAt") public void setEndsAt(Instant value) { endsAtPresent = true; endsAt = value; }
    @JsonSetter("location") public void setLocation(String value) { locationPresent = true; location = value; }
    @JsonSetter("attendanceOpensAt") public void setAttendanceOpensAt(Instant value) { attendanceOpensAtPresent = true; attendanceOpensAt = value; }
    @JsonSetter("attendanceClosesAt") public void setAttendanceClosesAt(Instant value) { attendanceClosesAtPresent = true; attendanceClosesAt = value; }
    @JsonSetter("status") public void setStatus(CourseSessionStatus value) { statusPresent = true; status = value; }

    public CourseSessionUpdateField<Integer> sessionNoUpdate() { return field(sessionNoPresent, sessionNo); }
    public CourseSessionUpdateField<Instant> startsAtUpdate() { return field(startsAtPresent, startsAt); }
    public CourseSessionUpdateField<Instant> endsAtUpdate() { return field(endsAtPresent, endsAt); }
    public CourseSessionUpdateField<String> locationUpdate() { return field(locationPresent, location); }
    public CourseSessionUpdateField<Instant> attendanceOpensAtUpdate() { return field(attendanceOpensAtPresent, attendanceOpensAt); }
    public CourseSessionUpdateField<Instant> attendanceClosesAtUpdate() { return field(attendanceClosesAtPresent, attendanceClosesAt); }
    public CourseSessionUpdateField<CourseSessionStatus> statusUpdate() { return field(statusPresent, status); }

    @JsonIgnore @AssertTrue(message = "at least one field is required")
    public boolean isNotEmpty() {
        return sessionNoPresent || startsAtPresent || endsAtPresent || locationPresent
            || attendanceOpensAtPresent || attendanceClosesAtPresent || statusPresent;
    }

    @JsonIgnore @AssertTrue(message = "startsAt must be before endsAt")
    public boolean isTimeRangeValid() {
        return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
    }

    @JsonIgnore @AssertTrue(message = "attendance window must be ordered")
    public boolean isAttendanceRangeValid() {
        return attendanceOpensAt == null || attendanceClosesAt == null
            || attendanceOpensAt.isBefore(attendanceClosesAt);
    }

    private <T> CourseSessionUpdateField<T> field(boolean present, T value) {
        return present ? CourseSessionUpdateField.present(value) : CourseSessionUpdateField.absent();
    }
}
