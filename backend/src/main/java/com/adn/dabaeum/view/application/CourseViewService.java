package com.adn.dabaeum.view.application;

import com.adn.dabaeum.view.domain.CourseViewRepository;
import com.adn.dabaeum.view.domain.ViewSaltRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 강좌 상세 조회 기록 (설계서 §8.1). 같은 사람은 하루 1건.
 * 로그인: SHA-256(userId) + userId 저장. 비로그인: HMAC-SHA256(당일 salt, ip‖UA), IP·UA 원문은 남기지 않는다.
 */
@Service
@Profile({"local", "dev"})
public class CourseViewService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CourseViewRepository views;
    private final ViewSaltRepository salts;
    private final Clock clock;

    public CourseViewService(CourseViewRepository views, ViewSaltRepository salts, Clock clock) {
        this.views = Objects.requireNonNull(views);
        this.salts = Objects.requireNonNull(salts);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 공개 강좌가 아니면 조용히 무시한다(존재 여부를 응답으로 알리지 않는다). */
    @Transactional
    public void record(UUID courseId, UUID userId, String ipAddress, String userAgent) {
        if (courseId == null || !views.isPublicCourse(courseId)) {
            return;
        }
        Instant now = clock.instant();
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        String viewerKey = userId != null
            ? sha256Hex(userId.toString())
            : hmacHex(salts.saltFor(today), String.valueOf(ipAddress) + "\n" + String.valueOf(userAgent));
        views.record(courseId, viewerKey, today, userId, now);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String hmacHex(byte[] salt, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }
}
