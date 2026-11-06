package com.adn.dabaeum.attendance.application;

import java.util.UUID;

/**
 * 학생 폰에 보여줄 만큼만 담는다. DID 암호문·다대구 sessionKey 는 절대 넣지 않는다 —
 * 이 값은 로그인하지 않은 단말이 조회한다.
 */
public record AttendSessionView(
    UUID id,
    AttendState state,
    String studentName,
    String errorCode,
    String deepLink
) {
}
