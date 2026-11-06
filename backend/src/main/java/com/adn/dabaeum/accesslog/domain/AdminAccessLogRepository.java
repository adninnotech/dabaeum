package com.adn.dabaeum.accesslog.domain;

import java.time.Instant;

/** 추가와 보존 기간 삭제만 있다. 수정은 없다(DB 트리거가 막는다). */
public interface AdminAccessLogRepository {

    void save(AdminAccessLog log);

    /** 최신순. 행위자 이름을 함께 읽는다. */
    AdminAccessLogPage search(AdminAccessLogCriteria criteria);

    int deleteCreatedBefore(Instant threshold);
}
