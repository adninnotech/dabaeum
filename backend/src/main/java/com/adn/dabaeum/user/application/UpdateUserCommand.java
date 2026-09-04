package com.adn.dabaeum.user.application;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateUserCommand(
    UUID userId,
    UserUpdateField<String> name,
    UserUpdateField<String> email,
    UserUpdateField<String> phone,
    UserUpdateField<LocalDate> birthDate,
    UserUpdateField<String> career,
    UserUpdateField<String> introduction,
    UserUpdateField<UUID> profileImageId
) {

    /** 프로필 확장 필드를 다루지 않는 기본 명령. */
    public UpdateUserCommand(
        UUID userId,
        UserUpdateField<String> name,
        UserUpdateField<String> email,
        UserUpdateField<String> phone,
        UserUpdateField<LocalDate> birthDate
    ) {
        this(userId, name, email, phone, birthDate,
            UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent());
    }

    public int presentFieldCount() {
        return count(name)
            + count(email)
            + count(phone)
            + count(birthDate)
            + count(career)
            + count(introduction)
            + count(profileImageId);
    }

    private int count(UserUpdateField<?> field) {
        return field != null && field.present() ? 1 : 0;
    }
}
