package com.adn.dabaeum.attendance.application.port;

import java.util.Objects;

/** 학생 한 명분 다대구 모바일웹 세션. deepLink 를 폰 브라우저가 열면 다대구 앱이 뜬다. */
public record DadaeguMobileWebSession(String sessionKey, String deepLink) {

    public DadaeguMobileWebSession {
        Objects.requireNonNull(sessionKey, "sessionKey");
        Objects.requireNonNull(deepLink, "deepLink");
    }
}
