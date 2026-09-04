package com.adn.dabaeum.attendance.domain;

public interface AttendanceQrTokenCodec {

    String encode(AttendanceQrTokenPayload payload);

    AttendanceQrTokenPayload decodeAndVerify(String token);

    String sha256(String token);
}
