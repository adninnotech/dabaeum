package com.adn.dabaeum.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(1);

    @Test
    void qrAttendanceRequiresAppSourceAndTokenButAllowsAdjustedStatus() {
        Attendance attendance = attendance(
            UUID.randomUUID(),
            AttendanceMethod.QR,
            AttendanceStatus.LATE,
            AttendanceSource.APP,
            UUID.randomUUID()
        );

        assertThat(attendance.status()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void rejectsQrAttendanceWithoutTokenOrWithWrongSource() {
        assertThatThrownBy(() -> attendance(
            UUID.randomUUID(), AttendanceMethod.QR, AttendanceStatus.PRESENT,
            AttendanceSource.APP, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> attendance(
            UUID.randomUUID(), AttendanceMethod.QR, AttendanceStatus.PRESENT,
            AttendanceSource.ADMIN_WEB, UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminAttendanceRequiresAdminWebSourceAndNoToken() {
        assertThat(attendance(
            UUID.randomUUID(), AttendanceMethod.ADMIN, AttendanceStatus.ABSENT,
            AttendanceSource.ADMIN_WEB, null
        )).isNotNull();

        assertThatThrownBy(() -> attendance(
            UUID.randomUUID(), AttendanceMethod.ADMIN, AttendanceStatus.PRESENT,
            AttendanceSource.ADMIN_WEB, UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attendance(
            UUID.randomUUID(), AttendanceMethod.ADMIN, AttendanceStatus.PRESENT,
            AttendanceSource.APP, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalAttendanceRetainsExplicitExternalSourceWithoutQrToken() {
        assertThat(attendance(
            UUID.randomUUID(), AttendanceMethod.EXTERNAL, AttendanceStatus.PRESENT,
            AttendanceSource.EXTERNAL_API, null
        )).isNotNull();
    }

    @Test
    void rejectsMissingIdentityAndReversedAuditTimes() {
        assertThatThrownBy(() -> new Attendance(
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            AttendanceMethod.QR,
            AttendanceStatus.PRESENT,
            null,
            AttendanceSource.APP,
            null,
            CREATED_AT,
            UPDATED_AT
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Attendance(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            AttendanceMethod.ADMIN,
            AttendanceStatus.PRESENT,
            null,
            AttendanceSource.ADMIN_WEB,
            null,
            UPDATED_AT,
            CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Attendance attendance(
        UUID enrollmentId,
        AttendanceMethod method,
        AttendanceStatus status,
        AttendanceSource source,
        UUID qrTokenId
    ) {
        return new Attendance(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            enrollmentId,
            qrTokenId,
            method,
            status,
            CREATED_AT,
            source,
            null,
            CREATED_AT,
            UPDATED_AT
        );
    }
}
