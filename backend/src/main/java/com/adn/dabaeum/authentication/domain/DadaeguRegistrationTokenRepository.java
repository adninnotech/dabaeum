package com.adn.dabaeum.authentication.domain;

import java.time.Instant;
import java.util.Optional;

public interface DadaeguRegistrationTokenRepository {

    void save(DadaeguRegistrationToken token);

    /**
     * 미사용·미만료 토큰을 사용 처리하고 돌려준다. 한 문장의 조건부 UPDATE 라 동시에 두 번 불러도 한 번만 성공한다.
     * 만료·재사용·없음은 모두 빈 값이다.
     */
    Optional<DadaeguRegistrationToken> consume(String tokenHash, Instant now);

    /** threshold 이전에 만료된 토큰을 사용 여부와 무관하게 지운다(설계서 §14: 만료 후 1일). 지운 행 수. */
    int deleteExpiredBefore(Instant threshold);
}
