package com.adn.dabaeum.attendance.domain;

public enum AttendanceMethod {
    QR,
    /** 강사 QR + 출석 시점 다대구 DID 재인증. 불변식은 QR과 같다. */
    QR_DADAEGU,
    ADMIN,
    EXTERNAL
}
