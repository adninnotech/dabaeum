package com.adn.dabaeum.user.api;

import com.adn.dabaeum.user.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UserStatusChangeRequest(@NotNull UserStatus status) {
}
