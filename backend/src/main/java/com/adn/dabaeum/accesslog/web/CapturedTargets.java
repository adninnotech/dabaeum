package com.adn.dabaeum.accesslog.web;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 응답 본문에서 모은 대상 사용자 id 와 행 수. */
record CapturedTargets(Set<UUID> userIds, int count) {

    static final CapturedTargets NONE = new CapturedTargets(Set.of(), 0);

    CapturedTargets {
        userIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(userIds));
    }
}
