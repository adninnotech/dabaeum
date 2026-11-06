package com.adn.dabaeum.attendance.api;

import java.time.Instant;

/**
 * 강사 화면이 QR 로 그릴 내용. {@code attendUrl} 은 서버가 조립한다 — 학생 폰이 닿을 주소를
 * 서버 설정 한 곳에서만 정하기 위해서다(강의실 망 한정으로 바꿀 때 프론트 배포가 필요 없다).
 * 프론트는 이 문자열을 그대로 QR 로 그리면 된다.
 */
public record AttendanceQrTokenResponse(String token, Instant expiresAt, String attendUrl) {
}
