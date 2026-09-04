package com.adn.dabaeum.role.infrastructure.mybatis;

import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class UserRoleMyBatisRepository implements UserRoleRepository {

    private final UserRoleMapper mapper;

    public UserRoleMyBatisRepository(UserRoleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(UserRoleAssignment assignment) {
        mapper.insert(toRow(assignment));
    }

    @Override
    public boolean saveIfAbsent(UserRoleAssignment assignment) {
        return mapper.insertIfAbsent(toRow(assignment)) == 1;
    }

    @Override
    public Optional<UserRoleAssignment> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<UserRoleAssignment> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id))
            .map(this::toDomain);
    }

    @Override
    public List<UserRoleAssignment> findByUserId(UUID userId) {
        return mapper.selectByUserId(userId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<UserRoleAssignment> findByUserIdAndInstitution(
        UUID userId,
        UUID institutionId
    ) {
        return mapper.selectByUserIdAndInstitution(userId, institutionId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<UserRoleAssignment> findByUserIdAndInstitutionForUpdate(
        UUID userId,
        UUID institutionId
    ) {
        return mapper.selectByUserIdAndInstitutionForUpdate(userId, institutionId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public boolean deleteByIdAndUserId(UUID roleId, UUID userId) {
        return mapper.deleteByIdAndUserId(roleId, userId) == 1;
    }

    @Override
    public boolean updateInstructorMemo(UUID institutionId, UUID userId, String memo) {
        return mapper.updateInstructorMemo(institutionId, userId, memo) >= 1;
    }

    private UserRoleRow toRow(UserRoleAssignment assignment) {
        return new UserRoleRow(
            assignment.id(),
            assignment.userId(),
            assignment.institutionId(),
            assignment.role().name(),
            assignment.createdAt()
        );
    }

    private UserRoleAssignment toDomain(UserRoleRow row) {
        return new UserRoleAssignment(
            row.id(),
            row.userId(),
            row.institutionId(),
            UserRole.valueOf(row.role()),
            row.createdAt()
        );
    }
}
