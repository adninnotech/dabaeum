package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;

/** 변경 시각이 NULL 이어도 "LOCAL 비밀번호가 있음" 을 구분하려고 hasLocal 을 같이 읽는다. */
public record PasswordAgeRow(boolean hasLocal, Instant passwordChangedAt) {
}
