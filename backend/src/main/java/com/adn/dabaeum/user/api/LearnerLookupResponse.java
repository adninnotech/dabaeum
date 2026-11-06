package com.adn.dabaeum.user.api;

import com.adn.dabaeum.user.domain.User;
import java.util.UUID;

/**
 * 대리 수강신청 대상 확인용 최소 정보(중앙 #56). 관리자는 이름·생년월일·휴대전화를 이미 들었으므로
 * 그 값과 연락처·주소는 다시 내려주지 않는다.
 */
public record LearnerLookupResponse(
    UUID userId,
    String name
) {

    public static LearnerLookupResponse from(User user) {
        return new LearnerLookupResponse(user.id(), user.name());
    }
}
