package com.adn.dabaeum.enrollment.domain;

import com.adn.dabaeum.user.domain.Gender;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 신청자 인적 사항 조각. 수강 신청·수강 현황 목록이 행마다 {@code /users/{id}} 를 다시 부르지
 * 않도록 조회 시점에 조인해 채운다(횡단 이슈 X-2). 개인정보(이메일·연락처·생년월일)를 담으므로
 * 관리자 응답에만 붙인다.
 */
public record EnrolleeProfile(
    String userName,
    LocalDate birthDate,
    Gender gender,
    String email,
    String phone,
    UUID profileImageId
) {
}
