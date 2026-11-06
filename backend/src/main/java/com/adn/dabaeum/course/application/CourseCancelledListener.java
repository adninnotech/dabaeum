package com.adn.dabaeum.course.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 과정이 CANCELLED 로 바뀐 직후 같은 트랜잭션 안에서 불린다. 과정 모듈은 구현체를 모른다.
 * 예외를 던지면 취소 자체가 롤백된다. (completion.application.CompletionConfirmedListener 와 같은 모양)
 */
public interface CourseCancelledListener {

    void onCourseCancelled(UUID courseId, UUID institutionId, Instant cancelledAt);
}
