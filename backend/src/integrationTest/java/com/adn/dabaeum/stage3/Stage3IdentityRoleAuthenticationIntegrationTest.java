package com.adn.dabaeum.stage3;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.identity.application.IdentityApplicationService;
import com.adn.dabaeum.identity.application.LinkIdentityCommand;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.application.AssignRoleCommand;
import com.adn.dabaeum.role.application.RoleApplicationService;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class Stage3IdentityRoleAuthenticationIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    IdentityApplicationService identityService;

    @Autowired
    RoleApplicationService roleService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Test
    void persistsIdentityAndInstitutionScopedRoleWithinRollbackBoundary() {
        User user = new User(
            UUID.randomUUID(),
            "Stage 3 acceptance user",
            null,
            null,
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            now(),
            now()
        );
        Institution institution = new Institution(
            UUID.randomUUID(),
            "STAGE3-" + UUID.randomUUID(),
            "Stage 3 acceptance institution",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            now(),
            now(),
            null
        );
        userRepository.save(user);
        institutionRepository.save(institution);

        UserIdentity identity = identityService.link(new LinkIdentityCommand(
            user.id(),
            IdentityProvider.DADAEGU,
            "stage3-subject-" + UUID.randomUUID(),
            "did:example:" + UUID.randomUUID(),
            true
        ));
        UserRoleAssignment role = roleService.assign(new AssignRoleCommand(
            user.id(),
            UserRole.INSTRUCTOR,
            institution.id()
        ));

        assertThat(identityService.list(user.id())).contains(identity);
        assertThat(roleService.list(user.id())).contains(role);
        assertThat(role.institutionId()).isEqualTo(institution.id());
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
