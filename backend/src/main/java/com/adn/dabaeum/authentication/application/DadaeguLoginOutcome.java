package com.adn.dabaeum.authentication.application;

import java.time.Instant;

/** 다대구 로그인 결과: 기존 사용자면 로그인됨, 처음이면 가입(구·군·약관)이 필요함 (설계서 §4.2). */
public sealed interface DadaeguLoginOutcome {

    record LoggedIn(LocalAuthResult result) implements DadaeguLoginOutcome {
    }

    record RegistrationRequired(
        String registrationToken,
        Instant expiresAt,
        String maskedName
    ) implements DadaeguLoginOutcome {
    }
}
