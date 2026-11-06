package com.adn.dabaeum.attendance.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 학생 폰이 강사 QR 주소의 {@code t} 값을 그대로 담아 보낸다. 길이 제한은 QR 출결 등록과 같다. */
public record AttendSessionCreateRequest(
    @NotNull @Size(min = 32, max = 2048) String token
) {
}
