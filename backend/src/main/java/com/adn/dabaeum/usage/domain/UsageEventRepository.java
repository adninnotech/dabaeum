package com.adn.dabaeum.usage.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UsageEventRepository {

    /** 구간값용 성별·생년월일. 회원 행이 없으면 empty. */
    Optional<MemberProfile> findMember(UUID userId);

    /** 하루 1건. 이미 있으면 아무것도 하지 않는다. */
    void record(UsageEvent event);

    /** 보존 기간(§9, 1년) 지난 원문 기록 삭제. */
    int deleteEventsBefore(LocalDate date);
}
