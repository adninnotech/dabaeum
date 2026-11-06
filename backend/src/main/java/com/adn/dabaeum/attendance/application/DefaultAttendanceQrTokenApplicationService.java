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
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAttendanceQrTokenApplicationService implements AttendanceQrTokenApplicationService {

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
        this(
                sessionRepository, courseRepository, instructorRepository,
                tokenRepository, codec, authorizationPolicy,
                properties, clock, new SecureRandom()
        );
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
            throw validation();
        }
        CourseSession session = sessionRepository.findByIdForUpdate(sessionId)
            .orElseThrow(this::sessionNotFound);
        Course course = courseRepository.findActiveById(session.courseId())
            .orElseThrow(this::courseNotFound);
        requireManager(context, course);

        Instant now = clock.instant();
        requireAttendanceOpen(session, now);
        Instant ttlExpiry = now.plus(properties.ttl());
        Instant closesAt = session.attendanceClosesAtOrEnd();
        Instant expiresAt = ttlExpiry.isBefore(closesAt) ? ttlExpiry : closesAt;   // 출결창 끝을 넘기지 않는다

        AttendanceQrTokenPayload payload = newPayload(sessionId, now, expiresAt);
        String rawToken = codec.encode(payload);
        tokenRepository.revokeActiveBySession(sessionId, now);
        tokenRepository.save(
                new AttendanceQrToken(
                        payload.tokenId(), sessionId, codec.sha256(rawToken),
                        context.userId(), now, expiresAt, null, now
                )
        );

        return new IssuedAttendanceQrToken(rawToken, expiresAt);
    }

    /** 토큰 본문. nonce 는 같은 회차·같은 초에 두 번 발급돼도 값이 달라지게 한다. */
    private AttendanceQrTokenPayload newPayload(UUID sessionId, Instant now, Instant expiresAt) {
        byte[] nonceBytes = new byte[32];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        return new AttendanceQrTokenPayload(1, UUID.randomUUID(), sessionId, now, expiresAt, nonce);
    }

    @Override
    @Transactional(readOnly = true)
    public ValidatedAttendanceQrToken validate(String rawToken, UUID expectedSessionId) {
        Instant now = clock.instant();

        AttendanceQrTokenPayload payload = decode(rawToken);
        requireNotExpired(payload.expiresAt(), now);
        requireSessionMatches(payload, expectedSessionId);

        AttendanceQrToken token = findIssued(rawToken, payload);
        requireNotExpired(token.expiresAt(), now);
        requireValid(token.isValidAt(now));

        return new ValidatedAttendanceQrToken(
            payload.tokenId(), payload.sessionId(), token.issuedBy(),
            payload.issuedAt(), payload.expiresAt()
        );
    }

    /** 서명이 맞아야 본문을 믿는다. 빈 값·깨진 서명은 모두 무효. */
    private AttendanceQrTokenPayload decode(String rawToken) {
        requireValid(rawToken != null && !rawToken.isBlank());
        try {
            return codec.decodeAndVerify(rawToken);
        } catch (InvalidAttendanceQrTokenException exception) {
            throw invalid();
        }
    }

    /**
     * expectedSessionId 가 null 이면 회차를 모른 채 토큰만 받은 경우다(학생이 QR URL 로 들어온 경로).
     * 회차는 토큰 자신이 서명해 담고 있으므로, 일치 검사만 건너뛰고 나머지 검증은 그대로 한다.
     */
    private void requireSessionMatches(AttendanceQrTokenPayload payload, UUID expectedSessionId) {
        requireValid(expectedSessionId == null || expectedSessionId.equals(payload.sessionId()));
    }

    /** 본문이 가리키는 토큰이 실제 발급된 그 행이고, 재발급으로 폐기되지 않았는가. */
    private AttendanceQrToken findIssued(String rawToken, AttendanceQrTokenPayload payload) {
        AttendanceQrToken token = tokenRepository.findByHash(codec.sha256(rawToken))
            .orElseThrow(this::invalid);
        requireValid(token.id().equals(payload.tokenId()));
        requireValid(token.sessionId().equals(payload.sessionId()));
        requireValid(token.revokedAt() == null);

        return token;
    }

    private void requireNotExpired(Instant expiresAt, Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw expired();
        }
    }

    private void requireManager(AuthenticatedUserContext context, Course course) {
        boolean mainAssigned = context != null
            && context.roles().stream().anyMatch(role ->
                "INSTRUCTOR".equals(
                        role.role()) && Objects.equals(role.institutionId(),
                        course.institutionId()
                )
            ) && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireCourseSessionManager(
            context, course.institutionId(), mainAssigned
        );
    }

    private void requireValid(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    /** OPEN 상태이고 지금이 출결창 안이어야 QR 을 낼 수 있다. */
    private void requireAttendanceOpen(CourseSession session, Instant now) {
        if (session.startsAt() == null) {
            throw conflict(ApiErrorCode.ATTENDANCE_CONFLICT,
                "Online session has no scheduled time, so it has no attendance window");
        }
        if (session.status() != CourseSessionStatus.OPEN) {
            throw conflict(ApiErrorCode.ATTENDANCE_CONFLICT, "Attendance session is not open");
        }
        if (now.isBefore(session.attendanceOpensAtOrStart())
                || !now.isBefore(session.attendanceClosesAtOrEnd())) {
            throw conflict(ApiErrorCode.ATTENDANCE_WINDOW_CLOSED, "Attendance window is closed");
        }
    }

    private ApiException sessionNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_SESSION_NOT_FOUND,
            "Course session not found"
        );
    }

    private ApiException courseNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.COURSE_NOT_FOUND, "Course not found"
        );
    }

    private ApiException validation() {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of("sessionId")
        );
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
