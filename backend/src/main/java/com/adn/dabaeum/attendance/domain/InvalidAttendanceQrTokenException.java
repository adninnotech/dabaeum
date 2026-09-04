package com.adn.dabaeum.attendance.domain;

public final class InvalidAttendanceQrTokenException extends RuntimeException {

    public InvalidAttendanceQrTokenException() {
        super("Invalid attendance QR token");
    }
}
