package com.adn.dabaeum.authentication.application;

import java.util.UUID;

public record ChangePasswordCommand(
    UUID userId,
    String currentPassword,
    String newPassword
) {
}
