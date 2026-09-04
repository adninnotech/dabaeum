package com.adn.dabaeum.authentication.api;

public record PasswordResetConfirmRequest(
    String token,
    String password
) {
}
