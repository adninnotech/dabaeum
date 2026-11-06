package com.adn.dabaeum.user.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record User(
    UUID id,
    String name,
    String email,
    String phone,
    LocalDate birthDate,
    UserStatus status,
    Instant withdrawnAt,
    Instant createdAt,
    Instant updatedAt,
    String career,
    String introduction,
    UUID profileImageId,
    Gender gender,
    Instant lastLoginAt,
    boolean emailNotificationEnabled,
    boolean smsNotificationEnabled,
    /** 사이트 관리자 전용 메모. B-4 회원 상세. 본인 조회(/users/me)에는 절대 내려가지 않는다. */
    String adminMemo
) {

    /** 이메일은 기본 수신, SMS 는 명시적으로 켠 사람에게만. V31 의 컬럼 기본값과 같다. */
    public static final boolean DEFAULT_EMAIL_NOTIFICATION_ENABLED = true;
    public static final boolean DEFAULT_SMS_NOTIFICATION_ENABLED = false;

    /** 프로필 확장 필드가 없는 기본 사용자. */
    public User(
        UUID id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        UserStatus status,
        Instant withdrawnAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
                id, name, email, phone, birthDate,
                status, withdrawnAt, createdAt, updatedAt,
            null, null, null, null, null,
            DEFAULT_EMAIL_NOTIFICATION_ENABLED, DEFAULT_SMS_NOTIFICATION_ENABLED,
            null
        );
    }

    /** 가입으로 새로 만드는 활성 사용자. */
    public static User registered(
        UUID id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        Instant now
    ) {
        return new User(id, name, email, phone, birthDate, UserStatus.ACTIVE, null, now, now);
    }

    /** 프로필 필드만 바뀐 복사본. 상태·탈퇴·생성 시각은 그대로. */
    public User withProfile(
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        String career,
        String introduction,
        UUID profileImageId,
        Instant updatedAt
    ) {
        return new User(
                id, name, email, phone, birthDate, status,
                withdrawnAt, createdAt, updatedAt, career,
                introduction, profileImageId, gender, lastLoginAt,
                emailNotificationEnabled, smsNotificationEnabled, adminMemo
        );
    }

    /** 성별만 바뀐 복사본. */
    public User withGender(Gender gender) {
        return new User(
                id, name, email, phone, birthDate, status,
                withdrawnAt, createdAt, updatedAt, career,
                introduction, profileImageId, gender, lastLoginAt,
                emailNotificationEnabled, smsNotificationEnabled, adminMemo
        );
    }

    /** 상태만 바뀐 복사본. */
    public User withStatus(UserStatus status, Instant withdrawnAt, Instant updatedAt) {
        return new User(
                id, name, email, phone, birthDate, status,
                withdrawnAt, createdAt, updatedAt, career,
                introduction, profileImageId, gender, lastLoginAt,
                emailNotificationEnabled, smsNotificationEnabled, adminMemo
        );
    }

    /** 알림 수신 설정만 바뀐 복사본. */
    public User withNotificationPreferences(
        boolean emailNotificationEnabled,
        boolean smsNotificationEnabled,
        Instant updatedAt
    ) {
        return new User(
                id, name, email, phone, birthDate, status,
                withdrawnAt, createdAt, updatedAt, career,
                introduction, profileImageId, gender, lastLoginAt,
                emailNotificationEnabled, smsNotificationEnabled, adminMemo
        );
    }

    /** 관리자 메모만 바뀐 복사본. */
    public User withAdminMemo(String adminMemo, Instant updatedAt) {
        return new User(
                id, name, email, phone, birthDate, status,
                withdrawnAt, createdAt, updatedAt, career,
                introduction, profileImageId, gender, lastLoginAt,
                emailNotificationEnabled, smsNotificationEnabled, adminMemo
        );
    }
}
