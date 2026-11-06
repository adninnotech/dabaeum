package com.adn.dabaeum.user.application;

import com.adn.dabaeum.user.domain.Gender;
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
    UserUpdateField<UUID> profileImageId,
    UserUpdateField<Gender> gender
) {

    /** null 은 "안 보냄"과 같다. 여기서 absent 로 정규화하면 쓰는 쪽은 null 을 신경 쓰지 않는다. */
    public UpdateUserCommand {
        name = orAbsent(name);
        email = orAbsent(email);
        phone = orAbsent(phone);
        birthDate = orAbsent(birthDate);
        career = orAbsent(career);
        introduction = orAbsent(introduction);
        profileImageId = orAbsent(profileImageId);
        gender = orAbsent(gender);
    }

    /** 프로필 확장 필드를 다루지 않는 기본 명령. */
    public UpdateUserCommand(
        UUID userId,
        UserUpdateField<String> name,
        UserUpdateField<String> email,
        UserUpdateField<String> phone,
        UserUpdateField<LocalDate> birthDate
    ) {
        this(userId, name, email, phone, birthDate,
            UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent(),
            UserUpdateField.absent());
    }

    public int presentFieldCount() {
        return count(name)
            + count(email)
            + count(phone)
            + count(birthDate)
            + count(career)
            + count(introduction)
            + count(profileImageId)
            + count(gender);
    }

    private static int count(UserUpdateField<?> field) {
        return field.present() ? 1 : 0;
    }

    private static <T> UserUpdateField<T> orAbsent(UserUpdateField<T> field) {
        return field == null ? UserUpdateField.absent() : field;
    }
}
