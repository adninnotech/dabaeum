package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class AttendanceCreateRequest {

    @NotNull
    private UUID enrollmentId;
    @NotNull
    private AttendanceMethod attendanceMethod;
    @NotNull
    private AttendanceStatus status;
    private Instant checkedAt;
    @NotNull
    private AttendanceSource source;
    @Size(min = 32, max = 2048)
    private String qrToken;

    public AttendanceCreateRequest() {
    }

    public UUID enrollmentId() {
        return enrollmentId;
    }

    public AttendanceMethod attendanceMethod() {
        return attendanceMethod;
    }

    public AttendanceStatus status() {
        return status;
    }

    public Instant checkedAt() {
        return checkedAt;
    }

    public AttendanceSource source() {
        return source;
    }

    public String qrToken() {
        return qrToken;
    }

    public void setEnrollmentId(UUID enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public void setAttendanceMethod(AttendanceMethod attendanceMethod) {
        this.attendanceMethod = attendanceMethod;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public void setSource(AttendanceSource source) {
        this.source = source;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }
}
