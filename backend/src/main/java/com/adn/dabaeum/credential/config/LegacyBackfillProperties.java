package com.adn.dabaeum.credential.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 전환 이전 발급분 백필 러너 (§11.3 2·3번, 계획 7). 서버에서 한 번 켜고 끈다. */
@ConfigurationProperties(prefix = "dabaeum.credential-proof.legacy-backfill")
public record LegacyBackfillProperties(boolean enabled) {
}
