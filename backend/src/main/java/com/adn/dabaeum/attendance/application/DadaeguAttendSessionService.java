package com.adn.dabaeum.attendance.application;

import java.util.Optional;
import java.util.UUID;

/** 학생이 강사 QR(URL)을 찍었을 때 열리는 다대구 인증 세션. 학생 한 명당 하나씩 생긴다. */
public interface DadaeguAttendSessionService {

    /** QR 토큰을 검증하고 학생 한 명분 다대구 세션을 연다. 결과를 기다리는 워커가 함께 시작된다. */
    AttendSessionView open(String rawToken);

    Optional<AttendSessionView> status(UUID attendId);
}
