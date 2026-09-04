package com.adn.dabaeum.user.application;

import com.adn.dabaeum.user.domain.UserStatus;
import java.time.LocalDate;

public record CreateUserCommand(
    String name,
    String email,
    String phone,
    LocalDate birthDate,
    UserStatus status
) {
}
