package com.adn.dabaeum.attendance.application;

import java.util.UUID;

/**
 * 다대구 DID 출결(D안). 학생이 강사 QR(URL)을 찍어 다대구 앱 인증을 마치면 서버가 대신 만든다.
 *
 * <p>QR 토큰은 <b>학생이 찍은 그 순간</b> 이미 검증됐고, 그 결과를 그대로 들고 온다.
 * 다시 검증하지 않는 이유: 두 시점 사이에 토큰은 서버 메모리에만 있어 아무도 손댈 수 없고,
 * 출석 자격은 "찍을 때 유효했는가"로 정해진다. 인증에 얼마나 걸렸는지는 상관없어야 한다.
 * (다시 검증하면 앱 켜는 데 시간을 끈 학생이 만료로 거부된다 — 2026-09-09 수정.)
 *
 * <p>회차·강사는 토큰이 서명해 담고 있으므로 따로 받지 않는다.
 */
public record RecordDadaeguAttendanceCommand(
    UUID userId,
    ValidatedAttendanceQrToken qrToken
) {
}
