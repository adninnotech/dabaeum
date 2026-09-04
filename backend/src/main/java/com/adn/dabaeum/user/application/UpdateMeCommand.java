package com.adn.dabaeum.user.application;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateMeCommand(
    UserUpdateField<String> name,
    UserUpdateField<String> email,
    UserUpdateField<String> phone,
    UserUpdateField<LocalDate> birthDate,
    UserUpdateField<String> career,
    UserUpdateField<String> introduction,
    UserUpdateField<UUID> profileImageId
) {

    /** 프로필 확장 필드를 다루지 않는 기본 명령. */
    public UpdateMeCommand(
        UserUpdateField<String> name,
        UserUpdateField<String> email,
        UserUpdateField<String> phone,
        UserUpdateField<LocalDate> birthDate
    ) {
        this(name, email, phone, birthDate,
            UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent());
    }
}
