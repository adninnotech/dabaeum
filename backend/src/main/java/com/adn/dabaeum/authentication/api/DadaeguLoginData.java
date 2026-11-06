package com.adn.dabaeum.authentication.api;

import java.time.Instant;

/** POST /auth/dadaegu/login 의 data. registrationRequired 로 두 모양을 구분한다 (설계서 §4.2). */
public sealed interface DadaeguLoginData {

    /** 기존 사용자. AuthToken 필드 그대로에 registrationRequired=false 만 더한다. */
    record LoggedIn(
        String accessToken,
        String tokenType,
        long expiresIn,
        SessionResponse session,
        boolean registrationRequired
    ) implements DadaeguLoginData {

        static LoggedIn of(AuthTokenResponse token) {
            return new LoggedIn(
                token.accessToken(), token.tokenType(), token.expiresIn(), token.session(), false);
        }
    }

    /** 처음 보는 DID. 계정·액세스 토큰은 아직 없다. */
    record RegistrationRequired(
        boolean registrationRequired,
        String registrationToken,
        Instant expiresAt,
        String maskedName
    ) implements DadaeguLoginData {

        RegistrationRequired(String registrationToken, Instant expiresAt, String maskedName) {
            this(true, registrationToken, expiresAt, maskedName);
        }
    }
}
