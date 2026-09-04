package com.adn.dabaeum.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
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

class UserIdentityMapperIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    UserIdentityRepository identityRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void savesAndFindsIdentityByIdAndProviderSubject() {
        User user = user();
        userRepository.save(user);
        UserIdentity identity = identity(
            user.id(),
            "subject-" + UUID.randomUUID(),
            "did:example:" + UUID.randomUUID()
        );

        identityRepository.save(identity);

        assertThat(identityRepository.findById(identity.id()))
            .contains(identity);
        assertThat(identityRepository.findByProviderSubject(
            identity.provider(),
            identity.providerSubject()
        )).contains(identity);
    }

    @Test
    void findsAllIdentitiesForUser() {
        User user = user();
        userRepository.save(user);
        UserIdentity first = identity(
            user.id(),
            "subject-" + UUID.randomUUID(),
            null
        );
        UserIdentity second = identity(
            user.id(),
            "subject-" + UUID.randomUUID(),
            null
        );
        identityRepository.save(first);
        identityRepository.save(second);

        assertThat(identityRepository.findByUserId(user.id()))
            .extracting(UserIdentity::id)
            .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void rejectsDuplicateProviderSubjectWithPostgresUniqueViolation() {
        User firstUser = user();
        User secondUser = user();
        userRepository.save(firstUser);
        userRepository.save(secondUser);
        String providerSubject = "duplicate-" + UUID.randomUUID();
        identityRepository.save(identity(firstUser.id(), providerSubject, null));

        assertSqlState("23505", () -> identityRepository.save(
            identity(secondUser.id(), providerSubject, null)
        ));
    }

    @Test
    void rejectsDuplicateNonNullExternalDidWithPostgresUniqueViolation() {
        User firstUser = user();
        User secondUser = user();
        userRepository.save(firstUser);
        userRepository.save(secondUser);
        String externalDid = "did:example:" + UUID.randomUUID();
        identityRepository.save(identity(
            firstUser.id(),
            "first-" + UUID.randomUUID(),
            externalDid
        ));

        assertSqlState("23505", () -> identityRepository.save(
            identity(
                secondUser.id(),
                "second-" + UUID.randomUUID(),
                externalDid
            )
        ));
    }

    private User user() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new User(
            UUID.randomUUID(),
            "Identity test user",
            null,
            null,
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            now,
            now
        );
    }

    private UserIdentity identity(
        UUID userId,
        String providerSubject,
        String externalDid
    ) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new UserIdentity(
            UUID.randomUUID(),
            userId,
            IdentityProvider.DADAEGU,
            providerSubject,
            externalDid,
            now,
            "{\"source\": \"integration-test\"}",
            now,
            now
        );
    }
}
