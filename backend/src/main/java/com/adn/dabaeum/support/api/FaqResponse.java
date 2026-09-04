package com.adn.dabaeum.support.api;

import java.util.UUID;

public record FaqResponse(
    UUID id,
    String question,
    String answer,
    int sortOrder
) {
}
