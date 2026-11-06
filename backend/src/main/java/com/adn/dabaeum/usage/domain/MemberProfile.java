package com.adn.dabaeum.usage.domain;

import java.time.LocalDate;

/** 구간값을 만들 때만 잠깐 읽는 회원 정보. 저장하지 않는다. */
public record MemberProfile(String gender, LocalDate birthDate) {
}
