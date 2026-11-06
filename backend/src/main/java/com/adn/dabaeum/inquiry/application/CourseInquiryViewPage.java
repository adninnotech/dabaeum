package com.adn.dabaeum.inquiry.application;

/**
 * 강좌별 문의 목록. 관리자(C-6)와 학습자(E-2)가 같은 엔드포인트를 쓰므로 응답에 담을 개인정보
 * 범위가 호출자마다 다르다. {@code manager} 는 <b>이 강좌를 기준으로</b> 판정한 결과다 —
 * 역할 이름만으로 판정하면 다른 기관 강사에게 문의자 연락처가 그대로 나간다.
 */
public record CourseInquiryViewPage(InquiryViewPage page, boolean manager) {
}
