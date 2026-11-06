package com.adn.dabaeum.user.api;

/**
 * 대리 수강신청 대상 조회 조건. 이름·생년월일·휴대전화가 접근 로그·브라우저 기록에 남지 않도록 URL 쿼리가 아니라
 * 본문으로 받는다. 세 값 모두 필수이고 검사는 서비스가 한다.
 */
public record LearnerLookupRequest(
    String name,
    String birthDate,
    String phone
) {
}
