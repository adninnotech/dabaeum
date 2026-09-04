package com.adn.dabaeum.support.api;

import com.adn.dabaeum.support.domain.TermsType;
import java.time.Instant;

public record TermsResponse(
    TermsType type,
    String title,
    String body,
    String version,
    Instant updatedAt
) {
}
