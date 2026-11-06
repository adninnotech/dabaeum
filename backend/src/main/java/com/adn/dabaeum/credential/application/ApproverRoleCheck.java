package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyException.Kind;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 승인 소비 직전의 승인자 권한 재확인 (설계서 §12.2). 승인 기록을 만든 뒤 PLATFORM_ADMIN 을 잃은 사람의 승인은
 * 소비하지 않는다. 승인 소비와 같은 트랜잭션(§6.2 T1)에서 부른다.
 */
@Component
class ApproverRoleCheck {

    private final UserRoleRepository userRoles;

    ApproverRoleCheck(UserRoleRepository userRoles) {
        this.userRoles = userRoles;
    }

    /** @throws SigningKeyException 승인자가 지금 PLATFORM_ADMIN 이 아니면 (소비 0행과 같은 거부) */
    void requirePlatformAdmin(UUID approverId) {
        boolean admin = userRoles.findByUserId(approverId).stream()
            .map(UserRoleAssignment::role)
            .anyMatch(role -> role == UserRole.PLATFORM_ADMIN);
        if (!admin) {
            throw new SigningKeyException(Kind.APPROVAL_REJECTED, "approver no longer holds PLATFORM_ADMIN");
        }
    }
}
