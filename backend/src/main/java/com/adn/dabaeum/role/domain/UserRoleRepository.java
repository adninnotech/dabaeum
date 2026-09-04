package com.adn.dabaeum.role.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository {

    void save(UserRoleAssignment assignment);

    boolean saveIfAbsent(UserRoleAssignment assignment);

    Optional<UserRoleAssignment> findById(UUID id);

    Optional<UserRoleAssignment> findByIdForUpdate(UUID id);

    List<UserRoleAssignment> findByUserId(UUID userId);

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
}
