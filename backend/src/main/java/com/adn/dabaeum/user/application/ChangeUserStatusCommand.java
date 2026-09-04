package com.adn.dabaeum.user.application;

import com.adn.dabaeum.user.domain.UserStatus;
import java.util.UUID;

public record ChangeUserStatusCommand(UUID userId, UserStatus status) {
}
