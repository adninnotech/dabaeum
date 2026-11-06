package com.adn.dabaeum.user.application;

import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.user.domain.User;
import java.util.List;
import java.util.UUID;

public interface UserApplicationService {

    User create(CreateUserCommand command);

    UserPage list(ListUsersQuery query);

    /**
     * 한 사용자의 역할. 변경 API 응답에도 roles 가 필수라 변경 직후 이 값을 붙인다.
     * 목록은 이 메서드를 쓰지 않는다 - 행마다 부르면 그게 N+1 이다.
     */
    List<RoleSummary> rolesOf(UUID userId);

    UserView get(UUID userId);

    User updateProfile(UpdateUserCommand command);

    User changeStatus(ChangeUserStatusCommand command);

    UserView getMe(UUID currentUserId);

    User updateMe(UUID currentUserId, UpdateMeCommand command);

    /** 관심 분야를 함께 고친다. 프로필 값이 하나도 없어도 된다. codes 가 비어 있으면 삭제·동의 철회다. */
    User updateMeWithInterests(UUID currentUserId, UpdateMeCommand command, List<String> codes, Boolean agreed);

    UserInterests interestsOf(UUID userId);

    /** 본인 탈퇴. 상태만 WITHDRAWN 으로 바꾸고 발급된 토큰을 무효화한다. */
    User withdrawSelf(UUID currentUserId);

    /**
     * 본인 알림 수신 설정 변경. {@code null} 은 "보내지 않음"이며 기존 설정을 그대로 둔다 —
     * 원시 boolean 으로 받으면 한쪽만 보낸 요청이 나머지 한쪽을 꺼 버린다.
     */
    User updateNotificationPreferences(
        UUID currentUserId,
        Boolean emailNotificationEnabled,
        Boolean smsNotificationEnabled
    );

    /**
     * 사이트 관리자 메모. {@code memo} 가 {@code null} 이면 지운다.
     * 값 검증(공백 제거·빈 문자열·길이)은 컨트롤러가 미리 끝낸 상태로 넘어온다.
     */
    User updateAdminMemo(UUID userId, String memo);
}
