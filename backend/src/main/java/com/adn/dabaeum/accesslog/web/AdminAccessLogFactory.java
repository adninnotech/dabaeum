package com.adn.dabaeum.accesslog.web;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLog;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 요청 하나를 접속기록 한 줄로 바꾼다. 남기지 않을 요청(관리자 아님, 본인만 대상)은 빈 값. */
final class AdminAccessLogFactory {

    static final int MAX_TARGET_IDS = 1000;
    static final int MAX_ENDPOINT = 200;
    static final int MAX_USER_AGENT = 500;

    private AdminAccessLogFactory() {
    }

    record AccessEvent(
        UUID id,
        AuthenticatedUserContext actor,
        AccessLogAction action,
        String endpoint,
        UUID pathUserId,
        CapturedTargets captured,
        int statusCode,
        String ipAddress,
        String userAgent,
        UUID requestId,
        Instant at
    ) {
        AccessEvent withUserAgent(String value) {
            return new AccessEvent(id, actor, action, endpoint, pathUserId, captured, statusCode,
                ipAddress, value, requestId, at);
        }
    }

    static Optional<AdminAccessLog> create(AccessEvent event) {
        Optional<String> role = adminRole(event.actor());
        if (role.isEmpty()) {
            return Optional.empty();
        }
        Set<UUID> targets = new LinkedHashSet<>();
        if (event.pathUserId() != null) {
            targets.add(event.pathUserId());
        }
        CapturedTargets captured = event.captured();
        if (captured != null) {
            targets.addAll(captured.userIds());
        }
        if (targets.equals(Set.of(event.actor().userId()))) {
            return Optional.empty();
        }
        int count = captured != null ? Math.max(captured.count(), targets.size()) : targets.size();
        List<UUID> stored = targets.isEmpty() || targets.size() > MAX_TARGET_IDS ? null : List.copyOf(targets);
        return Optional.of(new AdminAccessLog(
            event.id(), event.actor().userId(), role.get(), event.action(),
            truncate(event.endpoint(), MAX_ENDPOINT), stored, count, event.statusCode(),
            event.ipAddress(), truncate(event.userAgent(), MAX_USER_AGENT), event.requestId(), event.at()));
    }

    private static Optional<String> adminRole(AuthenticatedUserContext actor) {
        if (actor == null) {
            return Optional.empty();
        }
        Set<String> roles = actor.roles().stream()
            .map(AuthenticatedRole::role)
            .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
            .collect(java.util.stream.Collectors.toSet());
        if (roles.contains("PLATFORM_ADMIN")) {
            return Optional.of("PLATFORM_ADMIN");
        }
        if (roles.contains("INSTITUTION_ADMIN")) {
            return Optional.of("INSTITUTION_ADMIN");
        }
        return Optional.empty();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
