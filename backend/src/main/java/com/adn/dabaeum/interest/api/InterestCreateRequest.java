package com.adn.dabaeum.interest.api;

import java.util.UUID;

public record InterestCreateRequest(
    UUID courseId
) {
}
