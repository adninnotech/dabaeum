package com.adn.dabaeum.user.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 본인 탈퇴 요청 본문. {@code reason} 은 선택이며 저장하지 않는다 — 감사 목적으로 필요해지면
 * 컬럼을 추가해 채운다(이 작업 범위에서는 마이그레이션을 추가하지 않는다).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record WithdrawRequest(String reason) {
}
