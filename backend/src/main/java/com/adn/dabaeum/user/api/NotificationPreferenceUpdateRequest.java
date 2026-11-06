package com.adn.dabaeum.user.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 알림 수신 설정 변경 요청.
 *
 * <p>두 필드는 원시 {@code boolean} 이 아니라 {@code Boolean} 이다. 원시형이면 "안 보냄" 과
 * {@code false} 를 구분할 수 없어, 한쪽만 바꾸려는 요청이 나머지 한쪽을 꺼 버린다.
 * {@code null} 은 "이 값은 건드리지 말라"는 뜻이다.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record NotificationPreferenceUpdateRequest(
    Boolean emailNotificationEnabled,
    Boolean smsNotificationEnabled
) {
}
