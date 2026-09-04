package com.adn.dabaeum.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.config.AttendanceQrTokenProperties;
import com.adn.dabaeum.attendance.domain.AttendanceQrToken;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenCodec;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenPayload;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceQrTokenApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444");
    private static final UUID ADMIN_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");

    @Mock CourseSessionRepository sessionRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock AttendanceQrTokenRepository tokenRepository;
    @Mock AttendanceQrTokenCodec codec;

    private AttendanceQrTokenApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAttendanceQrTokenApplicationService(
            sessionRepository,
            courseRepository,
            instructorRepository,
            tokenRepository,
            codec,
            new AuthorizationPolicy(),
            new AttendanceQrTokenProperties(Duration.ofSeconds(60)),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SecureRandom()
        );
    }

    @Test
    void issuesThirtySecondTokenAndRevokesPreviousToken() {
        CourseSession session = session(CourseSessionStatus.OPEN, NOW.minusSeconds(60),
            NOW.plusSeconds(600));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(codec.encode(any())).thenReturn("encoded-token");
        when(codec.sha256("encoded-token"))
            .thenReturn("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");

        IssuedAttendanceQrToken issued = service.issue(SESSION_ID, globalAdmin());

        assertThat(issued.token()).isEqualTo("encoded-token");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        verify(tokenRepository).revokeActiveBySession(SESSION_ID, NOW);
        verify(tokenRepository).save(any(AttendanceQrToken.class));
    }

    @Test
    void capsExpiryAtAttendanceCloseAndRejectsClosedSessions() {
        CourseSession closing = session(CourseSessionStatus.OPEN, NOW.minusSeconds(60),
            NOW.plusSeconds(10));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(closing));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(codec.encode(any())).thenReturn("encoded-token");
        when(codec.sha256("encoded-token"))
            .thenReturn("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");

        assertThat(service.issue(SESSION_ID, globalAdmin()).expiresAt())
            .isEqualTo(NOW.plusSeconds(10));

        when(sessionRepository.findByIdForUpdate(SESSION_ID))
            .thenReturn(Optional.of(session(CourseSessionStatus.SCHEDULED,
                NOW.minusSeconds(60), NOW.plusSeconds(600))));
        assertThatThrownBy(() -> service.issue(SESSION_ID, globalAdmin()))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.ATTENDANCE_CONFLICT);
    }

    @Test
    void validatesStoredTokenAndDistinguishesExpiryFromInvalidity() {
        UUID tokenId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        AttendanceQrTokenPayload payload = new AttendanceQrTokenPayload(
            1, tokenId, SESSION_ID, NOW.minusSeconds(5), NOW.plusSeconds(25),
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        AttendanceQrToken stored = new AttendanceQrToken(
            tokenId, SESSION_ID,
            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            ADMIN_ID, payload.issuedAt(), payload.expiresAt(), null, payload.issuedAt());
        when(codec.decodeAndVerify("encoded-token")).thenReturn(payload);
        when(codec.sha256("encoded-token")).thenReturn(stored.tokenHash());
        when(tokenRepository.findByHash(stored.tokenHash())).thenReturn(Optional.of(stored));

        assertThat(service.validate("encoded-token", SESSION_ID).tokenId()).isEqualTo(tokenId);

        when(codec.decodeAndVerify("expired-token")).thenReturn(new AttendanceQrTokenPayload(
            1, tokenId, SESSION_ID, NOW.minusSeconds(60), NOW.minusSeconds(30), payload.nonce()));
        assertThatThrownBy(() -> service.validate("expired-token", SESSION_ID))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.ATTENDANCE_QR_EXPIRED);
    }

    private CourseSession session(CourseSessionStatus status, Instant opens, Instant closes) {
        return new CourseSession(SESSION_ID, COURSE_ID, 1, NOW.minusSeconds(120),
            NOW.plusSeconds(600), null, opens, closes, status, NOW.minusSeconds(120), NOW);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "COURSE-001", "출결 과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
            null, null, 20, null, null, false, null, CourseStatus.IN_PROGRESS,
            NOW.minusSeconds(120), NOW, null);
    }

    private AuthenticatedUserContext globalAdmin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
