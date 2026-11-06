package com.adn.dabaeum.authentication.application;

import java.time.LocalDate;

/**
 * 자체 가입 (설계서 §4.1). 약관은 이용약관·개인정보 필수 true,
 * 마케팅은 선택이다. 값 검증은 서비스가 한다.
 */
public record SignupCommand(
    String email,
    String password,
    String name,
    String phone,
    LocalDate birthDate,
    Boolean agreedToUsage,
    Boolean agreedToPrivacy,
    Boolean agreedToMarketing
) {
}
