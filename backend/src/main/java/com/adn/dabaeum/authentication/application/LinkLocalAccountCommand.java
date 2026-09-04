package com.adn.dabaeum.authentication.application;

import java.util.UUID;

/** 로그인된 사용자(대개 다대구 유저)에게 LOCAL 로그인 수단을 붙이는 명령. */
public record LinkLocalAccountCommand(UUID userId, String email, String password) {
}
