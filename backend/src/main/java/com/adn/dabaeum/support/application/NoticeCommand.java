package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import java.time.Instant;
import java.util.UUID;

public record NoticeCommand(
    UUID noticeId,
    String title,
    String body,
    NoticeAudience audience,
    NoticeStatus status,
    /** 예약 게시용. null 이면 기존 동작(게시 상태면 요청 시각). */
    Instant publishedAt,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
