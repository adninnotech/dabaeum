package com.adn.dabaeum.role.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultMyInstitutionApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID ROLE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    UserRoleRepository roleRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Test
    void 강사인_기관은_이름이_붙어서_나온다() {
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            assignment(UserRole.INSTRUCTOR, INSTITUTION_ID)
        ));
        when(institutionRepository.findByIds(Set.of(INSTITUTION_ID)))
            .thenReturn(List.of(institution(INSTITUTION_ID, "서울평생교육원")));

        List<MyInstitution> result = service().listMine(context(
            new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));

        assertThat(result).containsExactly(
            new MyInstitution(INSTITUTION_ID, "서울평생교육원", UserRole.INSTRUCTOR));
    }

    @Test
    void institutionId_가_없는_PLATFORM_ADMIN_은_빠진다() {
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            assignment(UserRole.PLATFORM_ADMIN, null)
        ));

        List<MyInstitution> result = service().listMine(context(
            new AuthenticatedRole("PLATFORM_ADMIN", null)));

        assertThat(result).isEmpty();
    }

    @Test
    void institutionId_가_있어도_LEARNER_는_빠진다() {
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            assignment(UserRole.LEARNER, INSTITUTION_ID)
        ));

        List<MyInstitution> result = service().listMine(context(
            new AuthenticatedRole("LEARNER", INSTITUTION_ID)));

        assertThat(result).isEmpty();
    }

    private DefaultMyInstitutionApplicationService service() {
        return new DefaultMyInstitutionApplicationService(
            roleRepository, institutionRepository);
    }

    private AuthenticatedUserContext context(AuthenticatedRole role) {
        return new AuthenticatedUserContext(USER_ID, "LOCAL", Set.of(role));
    }

    private UserRoleAssignment assignment(UserRole role, UUID institutionId) {
        return new UserRoleAssignment(ROLE_ID, USER_ID, institutionId, role, NOW);
    }

    private Institution institution(UUID id, String name) {
        return new Institution(
            id, "INST-1", name, null, null, null, null, null,
            InstitutionStatus.ACTIVE, NOW, NOW, null);
    }
}
