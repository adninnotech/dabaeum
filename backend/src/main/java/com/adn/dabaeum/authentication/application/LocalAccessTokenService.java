package com.adn.dabaeum.authentication.application;

import java.util.UUID;

public interface LocalAccessTokenService {

    default IssuedAccessToken issue(UUID userId) {
        return issue(userId, "LOCAL");
    }

    /** provider 는 IdentityProvider 이름(LOCAL, DADAEGU). */
    IssuedAccessToken issue(UUID userId, String provider);
}
