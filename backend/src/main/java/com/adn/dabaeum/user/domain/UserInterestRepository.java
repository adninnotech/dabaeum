package com.adn.dabaeum.user.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 회원 관심 분야(강좌 분류 1단계 코드) 저장소. */
public interface UserInterestRepository {

    List<String> findCodes(UUID userId);

    /** 회원의 관심 분야를 주어진 코드로 통째로 바꾼다. 빈 목록이면 모두 지운다. */
    void replace(UUID userId, List<String> codes, Instant now);
}
