package com.adn.dabaeum.authentication.domain;

import java.time.Instant;

/** LOCAL 비밀번호를 마지막으로 바꾼 시각. null 이면 관리자가 발급한 임시 비밀번호 — 본인이 바꾼 적 없다. */
public record PasswordAge(Instant changedAt) {
}
