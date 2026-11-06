package com.adn.dabaeum.user.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    void save(User user);

    Optional<User> findById(UUID id);


    /** 대리 수강신청 대상. 이름·생년월일·휴대전화(숫자만)가 모두 맞는 ACTIVE 학습자를 limit 건까지. */
    List<User> findActiveLearnersByPhone(String phoneDigits, String name, LocalDate birthDate, UUID institutionId, int limit);

    List<UserListItem> findPage(UserPageCriteria criteria);

    /** 목록 합계. 조건은 findPage 와 같은 필터를 같은 SQL 조각으로 건다. */
    long count(UserListFilter filter);

    boolean updateProfile(User user);

    boolean updateStatus(
        UUID id,
        UserStatus status,
        Instant withdrawnAt,
        Instant updatedAt
    );

    /** 이 시각보다 이르거나 같은 발급 시각의 토큰은 거부한다. 무효화 이력이 없으면 empty. */
    Optional<Instant> findAuthInvalidatedAt(UUID id);

    /** 비밀번호 재설정·계정 정지 등으로 기존 토큰을 즉시 무효화한다. 사용자가 없으면 false. */
    boolean invalidateTokens(UUID id, Instant at);

    /** 로그인 성공 시각을 기록한다. 프로필 수정이 아니므로 updated_at 은 건드리지 않는다. */
    void recordLastLogin(UUID userId, Instant at);

    /**
     * 알림 수신 설정만 바꾼다. 프로필 수정(updateProfile)이 이 컬럼을 덮지 않도록
     * 전용 UPDATE 로 분리했다. 사용자가 없으면 false.
     */
    boolean updateNotificationPreferences(
        UUID id,
        boolean emailNotificationEnabled,
        boolean smsNotificationEnabled,
        Instant updatedAt
    );

    /**
     * 사이트 관리자 메모만 바꾼다. updateProfile 이 이 컬럼을 덮지 않도록
     * 전용 UPDATE 로 분리했다. 사용자가 없으면 false.
     */
    boolean updateAdminMemo(UUID userId, String memo, Instant updatedAt);
}
