package com.adn.dabaeum.view.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.view.domain.CourseViewRepository;
import com.adn.dabaeum.view.domain.ViewSaltRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §8.1 — 조회자 키: 로그인은 SHA-256(userId), 비로그인은 HMAC(당일 salt, ip‖UA). 날짜는 서울 기준. */
@ExtendWith(MockitoExtension.class)
class CourseViewServiceTest {

    // UTC 2026-09-28 15:30 = 서울 9/29 00:30
    private static final Instant NOW = Instant.parse("2026-09-28T15:30:00Z");
    private static final LocalDate SEOUL_DAY = LocalDate.of(2026, 9, 29);
    private static final UUID COURSE = UUID.randomUUID();

    @Mock CourseViewRepository views;
    @Mock ViewSaltRepository salts;
    private CourseViewService service;

    @BeforeEach
    void setUp() {
        service = new CourseViewService(views, salts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void loggedInViewerKeyIsSha256OfUserIdAndKeepsUserId() {
        UUID user = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(views.isPublicCourse(COURSE)).thenReturn(true);

        service.record(COURSE, user, "203.0.113.7", "junit");

        verify(views).record(COURSE, CourseViewService.sha256Hex(user.toString()), SEOUL_DAY, user, NOW);
        verify(salts, never()).saltFor(any());
    }

    @Test
    void anonymousViewerKeyUsesDailySaltAndDropsRawAddress() {
        when(views.isPublicCourse(COURSE)).thenReturn(true);
        when(salts.saltFor(SEOUL_DAY)).thenReturn("salt-of-the-day-32-bytes-long!!!".getBytes(StandardCharsets.UTF_8));

        service.record(COURSE, null, "203.0.113.7", "junit");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(views).record(eq(COURSE), key.capture(), eq(SEOUL_DAY), eq(null), eq(NOW));
        assertThat(key.getValue()).hasSize(64).doesNotContain("203.0.113.7");
    }

    @Test
    void privateOrMissingCourseIsIgnored() {
        when(views.isPublicCourse(COURSE)).thenReturn(false);

        service.record(COURSE, null, "203.0.113.7", "junit");

        verify(views, never()).record(any(), any(), any(), any(), any());
    }
}
