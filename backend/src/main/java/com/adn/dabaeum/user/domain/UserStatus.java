package com.adn.dabaeum.user.domain;

public enum UserStatus {
    ACTIVE,
    DORMANT,
    WITHDRAWN,
    SUSPENDED;

    /** 탈퇴는 최종 상태다. 그 밖의 상태끼리는 서로 오갈 수 있고, 같은 상태로의 전이는 없다. */
    public boolean canTransitionTo(UserStatus target) {
        return this != WITHDRAWN && target != null && target != this;
    }
}
