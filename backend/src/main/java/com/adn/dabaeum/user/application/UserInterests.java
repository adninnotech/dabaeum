package com.adn.dabaeum.user.application;

import java.util.List;

/** 회원의 관심 분야와 가장 최근 선택 동의 상태. */
public record UserInterests(List<String> codes, boolean agreed) {

    public UserInterests {
        codes = List.copyOf(codes);
    }
}
