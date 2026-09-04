package com.adn.dabaeum.authentication.application;

import java.time.LocalDate;

public record SignupCommand(
    String email,
    String password,
    String name,
    String phone,
    LocalDate birthDate
) {
}
