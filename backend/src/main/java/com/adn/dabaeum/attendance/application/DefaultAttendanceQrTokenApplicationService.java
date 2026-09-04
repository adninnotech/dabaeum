package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.config.AttendanceQrTokenProperties;
import com.adn.dabaeum.attendance.domain.AttendanceQrToken;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenCodec;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenPayload;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository;
import com.adn.dabaeum.attendance.domain.InvalidAttendanceQrTokenException;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAttendanceQrTokenApplicationService
    implements AttendanceQrTokenApplicationService {

    private final CourseSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository instructorRepository;
    private final AttendanceQrTokenRepository tokenRepository;
    private final AttendanceQrTokenCodec codec;
    private final AuthorizationPolicy authorizationPolicy;
    private final AttendanceQrTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public DefaultAttendanceQrTokenApplicationService(
        CourseSessionRepository sessionRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        AttendanceQrTokenRepository tokenRepository,
        AttendanceQrTokenCodec codec,
        AuthorizationPolicy authorizationPolicy,
        AttendanceQrTokenProperties properties,
        Clock clock
    ) {
        this(sessionRepository, courseRepository, instructorRepository, tokenRepository, codec,
            authorizationPolicy, properties, clock, new SecureRandom());
    }

    public DefaultAttendanceQrTokenApplicationService(
        CourseSessionRepository sessionRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        AttendanceQrTokenRepository tokenRepository,
        AttendanceQrTokenCodec codec,
        AuthorizationPolicy authorizationPolicy,
        AttendanceQrTokenProperties properties,
        Clock clock,
        SecureRandom secureRandom
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
        this.instructorRepository = Objects.requireNonNull(
            instructorRepository, "instructorRepository");
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.authorizationPolicy = Objects.requireNonNull(
            authorizationPolicy, "authorizationPolicy");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    @Transactional
    public IssuedAttendanceQrToken issue(UUID sessionId, AuthenticatedUserContext context) {
        if (sessionId == null) {
            throw validation("sessionId");
        }
        CourseSession session = sessionRepository.findByIdForUpdate(sessionId)
            .orElseThrow(this::sessionNotFound);
        Course course = courseRepository.findActiveById(session.courseId())
            .orElseThrow(this::courseNotFound);
        requireManager(context, course);

        Instant now = clock.instant();
        if (session.status() != CourseSessionStatus.OPEN) {
            throw conflict(ApiErrorCode.ATTENDANCE_CONFLICT, "Attendance session is not open");
        }
        Instant opensAt = session.attendanceOpensAt() != null
            ? session.attendanceOpensAt() : session.startsAt();
        Instant closesAt = session.attendanceClosesAt() != null
            ? session.attendanceClosesAt() : session.endsAt();
        if (now.isBefore(opensAt) || !now.isBefore(closesAt)) {
            throw conflict(ApiErrorCode.ATTENDANCE_WINDOW_CLOSED,
                "Attendance window is closed");
        }

        Instant ttlExpiry = now.plus(properties.ttl());
        Instant expiresAt = ttlExpiry.isBefore(closesAt) ? ttlExpiry : closesAt;
        UUID tokenId = UUID.randomUUID();
        byte[] nonceBytes = new byte[32];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        AttendanceQrTokenPayload payload = new AttendanceQrTokenPayload(
            1, tokenId, sessionId, now, expiresAt, nonce);
        String rawToken = codec.encode(payload);
        tokenRepository.revokeActiveBySession(sessionId, now);
        tokenRepository.save(new AttendanceQrToken(
            tokenId, sessionId, codec.sha256(rawToken), context.userId(),
            now, expiresAt, null, now));
        return new IssuedAttendanceQrToken(rawToken, expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public ValidatedAttendanceQrToken validate(String rawToken, UUID expectedSessionId) {
        if (rawToken == null || rawToken.isBlank() || expectedSessionId == null) {
            throw invalid();
        }
        final AttendanceQrTokenPayload payload;
        try {
            payload = codec.decodeAndVerify(rawToken);
        } catch (InvalidAttendanceQrTokenException exception) {
            throw invalid();
        }
        Instant now = clock.instant();
        if (!now.isBefore(payload.expiresAt())) {
            throw expired();
        }
        if (!expectedSessionId.equals(payload.sessionId())) {
            throw invalid();
        }
        Optional<AttendanceQrToken> stored = tokenRepository.findByHash(codec.sha256(rawToken));
        if (stored.isEmpty()) {
            throw invalid();
        }
        AttendanceQrToken token = stored.get();
        if (!token.id().equals(payload.tokenId())
            || !token.sessionId().equals(payload.sessionId())) {
            throw invalid();
        }
        if (token.revokedAt() != null) {
            throw invalid();
        }
        if (!token.isValidAt(now)) {
            throw token.expiresAt().isAfter(now) ? invalid() : expired();
        }
        return new ValidatedAttendanceQrToken(
            payload.tokenId(), payload.sessionId(), payload.issuedAt(), payload.expiresAt());
    }

    private void requireManager(AuthenticatedUserContext context, Course course) {
        boolean mainAssigned = context != null
            && context.roles().stream().anyMatch(role ->
                "INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), course.institutionId()))
            && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireCourseSessionManager(
            context, course.institutionId(), mainAssigned);
    }

    private ApiException sessionNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_SESSION_NOT_FOUND,
            "Course session not found");
    }

    private ApiException courseNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND, "Course not found");
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }

    private ApiException conflict(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException invalid() {
        return conflict(ApiErrorCode.ATTENDANCE_QR_INVALID, "Attendance QR token is invalid");
    }

    private ApiException expired() {
        return conflict(ApiErrorCode.ATTENDANCE_QR_EXPIRED, "Attendance QR token is expired");
    }
}
