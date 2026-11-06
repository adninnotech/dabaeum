package com.adn.dabaeum.user.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UserMemoUpdateRequest(String memo) {
}
