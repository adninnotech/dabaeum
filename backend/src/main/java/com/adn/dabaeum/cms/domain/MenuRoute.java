package com.adn.dabaeum.cms.domain;

/**
 * 메뉴가 가리킬 수 있는 프론트 화면 키 (설계서 §7.2). 목록 밖 값은 422.
 * 프론트에 화면이 추가되면 프론트 배포와 함께 여기에 한 줄을 더한다.
 */
public enum MenuRoute {
    HOME,
    COURSES,
    POPULAR_COURSES,
    SEARCH,
    INSTITUTIONS,
    NOTICES,
    FAQS,
    INQUIRY,
    BADGES,
    MYPAGE,
    PORTFOLIO,
    SIGNUP
}
