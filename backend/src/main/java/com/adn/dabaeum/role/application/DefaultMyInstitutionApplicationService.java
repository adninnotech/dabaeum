package com.adn.dabaeum.role.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultMyInstitutionApplicationService
    implements MyInstitutionApplicationService {

    private static final Set<UserRole> INSTITUTION_SCOPED_ROLES =
        Set.of(UserRole.INSTRUCTOR, UserRole.INSTITUTION_ADMIN);

    private final UserRoleRepository roleRepository;
    private final InstitutionRepository institutionRepository;

    public DefaultMyInstitutionApplicationService(
        UserRoleRepository roleRepository,
        InstitutionRepository institutionRepository
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.institutionRepository = Objects.requireNonNull(
            institutionRepository, "institutionRepository");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyInstitution> listMine(AuthenticatedUserContext context) {
        List<UserRoleAssignment> scoped = roleRepository.findByUserId(context.userId())
            .stream()
            .filter(assignment -> assignment.institutionId() != null
                && INSTITUTION_SCOPED_ROLES.contains(assignment.role()))
            .toList();
        if (scoped.isEmpty()) {
            return List.of();
        }

        Set<UUID> institutionIds = scoped.stream()
            .map(UserRoleAssignment::institutionId)
            .collect(Collectors.toSet());
        Map<UUID, Institution> institutionsById = institutionRepository
            .findByIds(institutionIds)
            .stream()
            .collect(Collectors.toMap(Institution::id, Function.identity()));

        return scoped.stream()
            .map(assignment -> new MyInstitution(
                assignment.institutionId(),
                institutionName(institutionsById, assignment.institutionId()),
                assignment.role()))
            .toList();
    }

    private String institutionName(Map<UUID, Institution> institutionsById, UUID institutionId) {
        Institution institution = institutionsById.get(institutionId);
        return institution == null ? null : institution.name();
    }
}
