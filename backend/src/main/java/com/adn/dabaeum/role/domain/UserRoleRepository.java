package com.adn.dabaeum.role.domain;

import com.adn.dabaeum.institution.domain.InstitutionAdministrator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository {

    void save(UserRoleAssignment assignment);

    boolean saveIfAbsent(UserRoleAssignment assignment);

    Optional<UserRoleAssignment> findById(UUID id);

    Optional<UserRoleAssignment> findByIdForUpdate(UUID id);

    List<UserRoleAssignment> findByUserId(UUID userId);

    /**
     * 여러 사용자의 역할을 한 번에 가져온다. 목록 화면이 행마다 findByUserId 를 부르면
     * 100명이면 100번이라 N+1 이 된다. 빈 목록이면 질의하지 않고 빈 Map 을 준다.
     *
     * @return 사용자 id 별 역할 목록. 역할이 없는 사용자는 키 자체가 없다.
     */
    Map<UUID, List<RoleSummary>> findByUserIds(List<UUID> userIds);

    List<UserRoleAssignment> findByUserIdAndInstitution(
        UUID userId,
        UUID institutionId
    );

    List<UserRoleAssignment> findByUserIdAndInstitutionForUpdate(
        UUID userId,
        UUID institutionId
    );

    boolean deleteByIdAndUserId(UUID roleId, UUID userId);

    boolean updateInstructorMemo(UUID institutionId, UUID userId, String memo);

    /** 기관 상세 전용. 목록 화면에서 부르면 N+1 이 되므로 상세에서만 쓴다. */
    List<InstitutionAdministrator> findInstitutionAdministrators(UUID institutionId);
}
