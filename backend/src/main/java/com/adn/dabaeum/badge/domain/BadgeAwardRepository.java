package com.adn.dabaeum.badge.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** 자동 발급 전용 저장소 (설계서 §11.2·§11.4). */
public interface BadgeAwardRepository {

    /** 사용자의 COMPLETED 수료 수. classificationPrefix 가 있으면 그 분류(하위 포함) 강좌만. */
    long countCompletedCourses(UUID userId, String classificationPrefix);

    /** 사용자가 그 강좌를 COMPLETED 로 가진 수(0 또는 1). */
    long countCompletedCourse(UUID userId, UUID courseId);

    /** 사람·정의의 배지 행을 잠그고 읽는다(FOR UPDATE). 없으면 empty. */
    Optional<AwardedBadge> lockAwarded(UUID userId, UUID definitionId);

    /**
     * 정의 배지를 ISSUED 로 넣는다. 이미 행이 있으면(user_id, definition_id 유일) 아무것도 하지 않고 false.
     * 서버 두 대가 동시에 평가해도 한 건만 들어간다.
     */
    boolean insertIfAbsent(UUID badgeId, UUID userId, UUID courseId, BadgeDefinition definition, Integer tierLevel,
        Instant at);

    /** REVOKED 자동 배지를 ISSUED 로 되살린다. */
    boolean restore(UUID badgeId, UUID courseId, Integer tierLevel, Instant at);

    /** ISSUED 단계 배지의 단계를 바꾼다. */
    boolean changeLevel(UUID badgeId, int tierLevel, Instant at);

    /** 자동 발급(수료증 없이 정의로 준) ISSUED 배지. */
    List<AwardedBadge> findIssuedAutoBadges(UUID userId);

    /** ISSUED 배지를 REVOKED 로. 이미 폐기됐으면 false. */
    boolean revoke(UUID badgeId, Instant at);

    /**
     * credentialId 가 있으면 수료증에 붙여 준 수동 배지다. revokedByAdmin 이면 관리자가 사유를 달아 폐기한 배지라
     * 조건을 다시 채워도 자동으로 되살리지 않는다.
     */
    record AwardedBadge(UUID id, UUID definitionId, LearningBadgeStatus status, Integer tierLevel, UUID credentialId,
        boolean revokedByAdmin) {

        public AwardedBadge(UUID id, UUID definitionId) {
            this(id, definitionId, LearningBadgeStatus.ISSUED, null, null, false);
        }
    }
}
