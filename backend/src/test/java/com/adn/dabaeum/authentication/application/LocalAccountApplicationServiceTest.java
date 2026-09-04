package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalAccountApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(3600);
    private static final String RAW_PASSWORD = "learning-2026!";

    @Mock LocalAccountRepository localAccountRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserIdGenerator userIdGenerator;
    @Mock IdentityIdGenerator identityIdGenerator;
    @Mock RoleIdGenerator roleIdGenerator;
    @Mock LocalAccessTokenService tokenService;

    private PasswordEncoder passwordEncoder;
    private LocalAccountApplicationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new DefaultLocalAccountApplicationService(
            localAccountRepository,
            userRepository,
            userRoleRepository,
            userIdGenerator,
            identityIdGenerator,
            roleIdGenerator,
            passwordEncoder,
            tokenService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void signupCreatesActiveUserLocalCredentialAndGlobalLearner() {
        stubCompleteIssuance();

        LocalAuthResult result = service.signup(new SignupCommand(
            " Learner@Example.COM ",
            RAW_PASSWORD,
            " 홍길동 ",
            " 010-1234-5678 ",
            LocalDate.of(1995, 5, 10)
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().id()).isEqualTo(USER_ID);
        assertThat(userCaptor.getValue().email()).isEqualTo("learner@example.com");
        assertThat(userCaptor.getValue().name()).isEqualTo("홍길동");
        assertThat(userCaptor.getValue().phone()).isEqualTo("010-1234-5678");
        assertThat(userCaptor.getValue().status()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<LocalAccountCredential> credentialCaptor =
            ArgumentCaptor.forClass(LocalAccountCredential.class);
        verify(localAccountRepository).save(credentialCaptor.capture());
        assertThat(credentialCaptor.getValue().normalizedEmail())
            .isEqualTo("learner@example.com");
        assertThat(credentialCaptor.getValue().passwordHash())
            .isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(
            RAW_PASSWORD,
            credentialCaptor.getValue().passwordHash()
        )).isTrue();

        verify(userRoleRepository).save(new UserRoleAssignment(
            ROLE_ID,
            USER_ID,
            null,
            UserRole.LEARNER,
            NOW
        ));
        assertThat(result.accessToken()).isEqualTo("signed-access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.provider()).isEqualTo("LOCAL");
        assertThat(result.roles()).singleElement().satisfies(role -> {
            assertThat(role.role()).isEqualTo("LEARNER");
            assertThat(role.institutionId()).isNull();
        });
    }

    @Test
    void signupRejectsDuplicateEmailAndConcurrentIdentityConflict() {
        when(localAccountRepository.findByNormalizedEmail("learner@example.com"))
            .thenReturn(Optional.of(credential(USER_ID, RAW_PASSWORD)));

        assertApiError(
            () -> service.signup(signup("learner@example.com", RAW_PASSWORD)),
            409,
            ApiErrorCode.IDENTITY_CONFLICT
        );
        verify(userRepository, never()).save(any());

        when(localAccountRepository.findByNormalizedEmail("other@example.com"))
            .thenReturn(Optional.empty());
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
            .when(localAccountRepository).save(any());
        assertApiError(
            () -> service.signup(signup("other@example.com", RAW_PASSWORD)),
            409,
            ApiErrorCode.IDENTITY_CONFLICT
        );
    }

    @Test
    void signupMapsConcurrentUserEmailConflictToIdentityConflict() {
        when(localAccountRepository.findByNormalizedEmail("learner@example.com"))
            .thenReturn(Optional.empty());
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
            .when(userRepository).save(any());

        assertApiError(
            () -> service.signup(signup("learner@example.com", RAW_PASSWORD)),
            409,
            ApiErrorCode.IDENTITY_CONFLICT
        );
        verify(localAccountRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void signupRejectsShortAndUtf8Over72BytePasswords() {
        assertApiError(
            () -> service.signup(signup("one@example.com", "short")),
            422,
            ApiErrorCode.VALIDATION_FAILED
        );
        String tooLong = "가".repeat(25);
        assertThat(tooLong.getBytes(StandardCharsets.UTF_8).length)
            .isGreaterThan(72);
        assertApiError(
            () -> service.signup(signup("two@example.com", tooLong)),
            422,
            ApiErrorCode.VALIDATION_FAILED
        );
    }

    @Test
    void loginNormalizesEmailAndReturnsCurrentDatabaseRoles() {
        when(tokenService.issue(USER_ID)).thenReturn(new IssuedAccessToken(
            "signed-access-token",
            EXPIRES_AT
        ));
        String hash = passwordEncoder.encode(RAW_PASSWORD);
        when(localAccountRepository.findByNormalizedEmail("learner@example.com"))
            .thenReturn(Optional.of(credential(USER_ID, hash)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            new UserRoleAssignment(ROLE_ID, USER_ID, null, UserRole.LEARNER, NOW)
        ));

        LocalAuthResult result = service.login(new LoginCommand(
            " Learner@Example.COM ",
            RAW_PASSWORD
        ));

        verify(localAccountRepository).findByNormalizedEmail("learner@example.com");
        verify(tokenService).issue(USER_ID);
        assertThat(result.roles()).singleElement()
            .satisfies(role -> assertThat(role.role()).isEqualTo("LEARNER"));
    }

    @Test
    void loginUsesOneFailureForUnknownWrongPasswordAndInactiveUser() {
        assertAuthenticationFailed(() -> service.login(new LoginCommand(
            "missing@example.com",
            RAW_PASSWORD
        )));

        String hash = passwordEncoder.encode(RAW_PASSWORD);
        when(localAccountRepository.findByNormalizedEmail("learner@example.com"))
            .thenReturn(Optional.of(credential(USER_ID, hash)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        assertAuthenticationFailed(() -> service.login(new LoginCommand(
            "learner@example.com",
            "wrong-password"
        )));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(
            USER_ID,
            "휴면 사용자",
            "learner@example.com",
            null,
            null,
            UserStatus.DORMANT,
            null,
            NOW,
            NOW
        )));
        assertAuthenticationFailed(() -> service.login(new LoginCommand(
            "learner@example.com",
            RAW_PASSWORD
        )));
    }

    private SignupCommand signup(String email, String password) {
        return new SignupCommand(email, password, "학습자", null, null);
    }

    private void stubCompleteIssuance() {
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);
        when(roleIdGenerator.generate()).thenReturn(ROLE_ID);
        when(tokenService.issue(USER_ID)).thenReturn(new IssuedAccessToken(
            "signed-access-token",
            EXPIRES_AT
        ));
    }

    private LocalAccountCredential credential(UUID userId, String hash) {
        return new LocalAccountCredential(
            IDENTITY_ID,
            userId,
            "learner@example.com",
            hash,
            NOW,
            NOW
        );
    }

    @Test
    void linkLocalAddsCredentialToExistingUserAndFillsEmail() {
        User dadaeguUser = new User(
            USER_ID, "홍길동", null, "01012345678", LocalDate.of(1995, 12, 5),
            UserStatus.ACTIVE, null, NOW, NOW
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(dadaeguUser));
        when(localAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(localAccountRepository.findByNormalizedEmail("me@example.com"))
            .thenReturn(Optional.empty());
        when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);

        LocalAccountCredential credential = service.linkLocal(
            new LinkLocalAccountCommand(USER_ID, " Me@Example.COM ", RAW_PASSWORD)
        );

        assertThat(credential.identityId()).isEqualTo(IDENTITY_ID);
        assertThat(credential.userId()).isEqualTo(USER_ID);
        assertThat(credential.normalizedEmail()).isEqualTo("me@example.com");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, credential.passwordHash())).isTrue();
        assertThat(credential.createdAt()).isEqualTo(NOW);
        verify(localAccountRepository).save(credential);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateProfile(userCaptor.capture());
        User updated = userCaptor.getValue();
        assertThat(updated.email()).isEqualTo("me@example.com");
        assertThat(updated.name()).isEqualTo("홍길동");
        assertThat(updated.phone()).isEqualTo("01012345678");
        assertThat(updated.birthDate()).isEqualTo(LocalDate.of(1995, 12, 5));
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userRoleRepository, tokenService, userIdGenerator, roleIdGenerator);
    }

    @Test
    void linkLocalRejectsUserWhoAlreadyHasLocalCredential() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(localAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
            new LocalAccountCredential(
                IDENTITY_ID, USER_ID, "learner@example.com", "$2a$04$hash", NOW, NOW)
        ));

        assertApiError(
            () -> service.linkLocal(
                new LinkLocalAccountCommand(USER_ID, "new@example.com", RAW_PASSWORD)),
            409,
            ApiErrorCode.IDENTITY_CONFLICT
        );
        verify(localAccountRepository, never()).save(any());
        verify(userRepository, never()).updateProfile(any());
    }

    @Test
    void linkLocalRejectsEmailOwnedByAnotherAccount() {
        UUID otherUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(localAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(localAccountRepository.findByNormalizedEmail("taken@example.com"))
            .thenReturn(Optional.of(new LocalAccountCredential(
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                otherUserId, "taken@example.com", "$2a$04$hash", NOW, NOW)));

        assertApiError(
            () -> service.linkLocal(
                new LinkLocalAccountCommand(USER_ID, "taken@example.com", RAW_PASSWORD)),
            409,
            ApiErrorCode.IDENTITY_CONFLICT
        );
        verify(localAccountRepository, never()).save(any());
    }

    @Test
    void linkLocalValidatesPasswordLikeSignup() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> service.linkLocal(
            new LinkLocalAccountCommand(USER_ID, "me@example.com", "short")))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(422);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
                assertThat(exception.details()).containsExactly("password");
            });
        verify(localAccountRepository, never()).save(any());
    }

    private User activeUser() {
        return new User(
            USER_ID,
            "학습자",
            "learner@example.com",
            null,
            null,
            UserStatus.ACTIVE,
            null,
            NOW,
            NOW
        );
    }

    private void assertAuthenticationFailed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(401);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.AUTHENTICATION_FAILED);
                assertThat(exception.getMessage()).isEqualTo("Authentication failed");
            });
    }

    private void assertApiError(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
        int status,
        ApiErrorCode code
    ) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(status);
                assertThat(exception.code()).isEqualTo(code);
            });
    }
}
