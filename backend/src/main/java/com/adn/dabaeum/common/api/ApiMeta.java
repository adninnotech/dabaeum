package com.adn.dabaeum.common.api;

import java.time.OffsetDateTime;

public record ApiMeta(String requestId, OffsetDateTime timestamp) {
}
