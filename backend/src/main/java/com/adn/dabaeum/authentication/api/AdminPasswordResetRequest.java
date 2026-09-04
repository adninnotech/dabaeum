package com.adn.dabaeum.authentication.api;

public record AdminPasswordResetRequest(
    String temporaryPassword
) {
}
