package com.adn.dabaeum.user.domain;

import java.util.List;
import java.util.UUID;

/**
 * 약관 동의 이력 저장소. append-only — 갱신 메서드를 두지 않는다.
 * 철회·재동의도 saveAll 로 새 행을 추가한다.
 */
public interface UserAgreementRepository {

    void saveAll(List<UserAgreement> agreements);

    List<UserAgreement> findByUserId(UUID userId);
}
