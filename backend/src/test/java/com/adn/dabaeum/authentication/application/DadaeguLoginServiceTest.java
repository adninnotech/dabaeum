package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.DadaeguRegistrationToken;
import com.adn.dabaeum.authentication.domain.DadaeguRegistrationTokenRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.application.UserInterestService;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Cipher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DadaeguLoginServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final String DID = "did:daegu:DVdSm5VTZtestonly";
    private static final String NAME = "홍길동";
    private static final String BIRTHDATE = "19951205";
    private static final String PHONE = "01012345678";

    private static KeyPair keyPair;

    @Mock UserIdentityRepository identityRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserIdGenerator userIdGenerator;
    @Mock IdentityIdGenerator identityIdGenerator;
    @Mock RoleIdGenerator roleIdGenerator;
    @Mock LocalAccessTokenService tokenService;
    @Mock DadaeguRegistrationTokenRepository registrationTokenRepository;
    @Mock UserAgreementRepository agreementRepository;
    @Mock
    private UserInterestService interestService;

    private DadaeguLoginService service;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        service = new DadaeguLoginService(
            new DadaeguLoginProperties("SITE-1", pem),
            identityRepository,
            userRepository,
            userRoleRepository,
            userIdGenerator,
            identityIdGenerator,
            roleIdGenerator,
            tokenService,
            registrationTokenRepository,
            agreementRepository,
            interestService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void firstLoginCreatesNoAccountAndReturnsARegistrationToken() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.empty());

        DadaeguLoginOutcome outcome = service.login(
            encrypt(DID), encrypt(NAME), encrypt(BIRTHDATE), encrypt(PHONE));

        DadaeguLoginOutcome.RegistrationRequired required =
            (DadaeguLoginOutcome.RegistrationRequired) outcome;
        assertThat(required.registrationToken()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(required.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(required.maskedName()).isEqualTo("홍*동");

        ArgumentCaptor<DadaeguRegistrationToken> saved =
            ArgumentCaptor.forClass(DadaeguRegistrationToken.class);
        verify(registrationTokenRepository).save(saved.capture());
        assertThat(saved.getValue().tokenHash())
            .isEqualTo(OpaqueTokens.sha256Hex(required.registrationToken()));
        assertThat(saved.getValue().did()).isEqualTo(DID);
        assertThat(saved.getValue().name()).isEqualTo(NAME);
        assertThat(saved.getValue().phone()).isEqualTo(PHONE);
        assertThat(saved.getValue().birthDate()).isEqualTo(LocalDate.of(1995, 12, 5));
        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
        verifyNoInteractions(tokenService, userRoleRepository);
    }

    @Test
    void firstLoginWithoutANameClaimIsRejected() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.empty());

        assertAuthenticationFailed(encrypt(DID), null, null, null);
        verifyNoInteractions(registrationTokenRepository);
    }

    @Test
    void signupConsumesTheTokenAndCreatesUserIdentityRoleAndAgreements() {
        when(registrationTokenRepository.consume(OpaqueTokens.sha256Hex("raw-token"), NOW))
            .thenReturn(Optional.of(pendingToken()));
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.empty());
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);
        when(roleIdGenerator.generate()).thenReturn(ROLE_ID);
        when(tokenService.issue(USER_ID, "DADAEGU"))
            .thenReturn(new IssuedAccessToken("signed-access-token", NOW.plusSeconds(3600)));

        LocalAuthResult result = service.signup(
            new DadaeguSignupCommand(" raw-token ", List.of("TSIX_040000"), true, true, true, false));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().name()).isEqualTo(NAME);
        assertThat(user.getValue().phone()).isEqualTo(PHONE);
        assertThat(user.getValue().email()).isNull();
        ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identityRepository).save(identity.capture());
        assertThat(identity.getValue().providerSubject()).isEqualTo(DID);
        assertThat(identity.getValue().verifiedAt()).isEqualTo(NOW);
        verify(userRoleRepository).save(learner());
        verify(agreementRepository).saveAll(anyList());
        verify(interestService).validateForSignup(List.of("TSIX_040000"), true);
        verify(interestService).recordAtSignup(USER_ID, List.of("TSIX_040000"), true, NOW);
        verify(userRepository).recordLastLogin(USER_ID, NOW);
        assertThat(result.accessToken()).isEqualTo("signed-access-token");
        assertThat(result.provider()).isEqualTo("DADAEGU");
    }

    @Test
    void invalidInterestIsRejectedBeforeTheTokenIsConsumed() {
        ApiException rejected = new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED, "bad");
        org.mockito.Mockito.doThrow(rejected).when(interestService).validateForSignup(List.of("BAD"), true);

        assertThatThrownBy(() -> service.signup(new DadaeguSignupCommand("raw-token", List.of("BAD"), true, true, true, false)))
            .isSameAs(rejected);

        verifyNoInteractions(registrationTokenRepository, userRepository);
    }

    @Test
    void signupValidatesAgreementsBeforeTouchingTheToken() {
        for (DadaeguSignupCommand bad : List.of(
            new DadaeguSignupCommand("raw-token", null, null, false, true, false),
            new DadaeguSignupCommand("raw-token", null, null, true, null, false))) {
            ApiException exception = catchThrowableOfType(
                () -> service.signup(bad), ApiException.class);
            assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        }
        verifyNoInteractions(registrationTokenRepository, userRepository);
    }

    @Test
    void unusableTokenIs401() {
        when(registrationTokenRepository.consume(any(), any())).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(() ->
            service.signup(new DadaeguSignupCommand("raw-token", null, null, true, true, false)),
            ApiException.class);

        assertThat(exception.status().value()).isEqualTo(401);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.AUTHENTICATION_FAILED);
        verifyNoInteractions(userRepository);
    }

    @Test
    void didRegisteredMeanwhileIs409() {
        when(registrationTokenRepository.consume(any(), any()))
            .thenReturn(Optional.of(pendingToken()));
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));

        ApiException exception = catchThrowableOfType(() ->
            service.signup(new DadaeguSignupCommand("raw-token", null, null, true, true, false)),
            ApiException.class);

        assertThat(exception.code()).isEqualTo(ApiErrorCode.IDENTITY_CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void masksAllButFirstAndLastCharacter() {
        assertThat(DadaeguLoginService.maskName("홍길동")).isEqualTo("홍*동");
        assertThat(DadaeguLoginService.maskName("남궁민수")).isEqualTo("남**수");
        assertThat(DadaeguLoginService.maskName("홍길")).isEqualTo("홍*");
        assertThat(DadaeguLoginService.maskName("홍")).isEqualTo("*");
    }

    @Test
    void repeatedLoginRefreshesChangedProfileFromDadaegu() throws Exception {
        User existing = new User(
            USER_ID, "개명전이름", "keep@example.com", "01000000000",
            LocalDate.of(1995, 12, 5), UserStatus.ACTIVE, null, NOW, NOW,
            "경력", "소개", null, null, null, true, false, null
        );
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(learner()));
        when(tokenService.issue(USER_ID, "DADAEGU")).thenReturn(new IssuedAccessToken(
            "signed-access-token",
            NOW.plusSeconds(3600)
        ));

        service.login(encrypt(DID), encrypt(NAME), encrypt(BIRTHDATE), encrypt(PHONE));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateProfile(captor.capture());
        User updated = captor.getValue();
        assertThat(updated.name()).isEqualTo(NAME);
        assertThat(updated.phone()).isEqualTo(PHONE);
        assertThat(updated.birthDate()).isEqualTo(LocalDate.of(1995, 12, 5));
        assertThat(updated.email()).isEqualTo("keep@example.com");
        assertThat(updated.career()).isEqualTo("경력");
        assertThat(updated.introduction()).isEqualTo("소개");

        verify(userRepository).recordLastLogin(USER_ID, NOW);
        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
        verifyNoInteractions(userIdGenerator, identityIdGenerator, roleIdGenerator);
    }

    @Test
    void repeatedLoginWithoutClaimsKeepsExistingProfile() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.ACTIVE)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(learner()));
        when(tokenService.issue(USER_ID, "DADAEGU")).thenReturn(new IssuedAccessToken(
            "signed-access-token",
            NOW.plusSeconds(3600)
        ));

        LocalAuthResult result = ((DadaeguLoginOutcome.LoggedIn)
            service.login(encrypt(DID), null, null, null)).result();

        assertThat(result.userId()).isEqualTo(USER_ID);
        verify(userRepository, never()).updateProfile(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsBlankUndecryptableAndNonDidPayloads() throws Exception {
        assertAuthenticationFailed(null, null, null, null);
        assertAuthenticationFailed("   ", null, null, null);
        assertAuthenticationFailed("not-base64!!", null, null, null);
        assertAuthenticationFailed(Base64.getEncoder().encodeToString(
            "plain-text-not-rsa".getBytes(StandardCharsets.UTF_8)
        ), null, null, null);
        assertAuthenticationFailed(encrypt("홍길동"), null, null, null);

        verifyNoInteractions(identityRepository, userRepository, tokenService);
    }

    @Test
    void rejectsBrokenOrMalformedClaims() throws Exception {
        assertAuthenticationFailed(encrypt(DID), "not-base64!!", null, null);
        assertAuthenticationFailed(encrypt(DID), null, encrypt("1995-12-05"), null);
        assertAuthenticationFailed(encrypt(DID), null, encrypt("생일아님"), null);

        verifyNoInteractions(identityRepository, userRepository, tokenService);
    }

    @Test
    void rejectsInactiveUser() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.SUSPENDED)));

        assertAuthenticationFailed(encrypt(DID), null, null, null);
        verifyNoInteractions(tokenService);
    }

    @Test
    void linkAttachesDadaeguIdentityToCurrentUserAndRefreshesProfile() throws Exception {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(new User(
                USER_ID, "가입시이름", "me@example.com", null, null,
                UserStatus.ACTIVE, null, NOW, NOW
            )));
        when(identityRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.empty());
        when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);

        UserIdentity linked = service.link(
            USER_ID, encrypt(DID), encrypt(NAME), encrypt(BIRTHDATE), encrypt(PHONE)
        );

        assertThat(linked.id()).isEqualTo(IDENTITY_ID);
        assertThat(linked.userId()).isEqualTo(USER_ID);
        assertThat(linked.provider()).isEqualTo(IdentityProvider.DADAEGU);
        assertThat(linked.providerSubject()).isEqualTo(DID);
        assertThat(linked.externalDid()).isEqualTo(DID);
        assertThat(linked.verifiedAt()).isEqualTo(NOW);
        verify(identityRepository).save(linked);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateProfile(userCaptor.capture());
        User updated = userCaptor.getValue();
        assertThat(updated.name()).isEqualTo(NAME);
        assertThat(updated.phone()).isEqualTo(PHONE);
        assertThat(updated.birthDate()).isEqualTo(LocalDate.of(1995, 12, 5));
        assertThat(updated.email()).isEqualTo("me@example.com");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userRoleRepository, tokenService);
    }

    @Test
    void linkRejectsDidAlreadyBoundToAnyUser() throws Exception {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.ACTIVE)));
        when(identityRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));

        assertLinkConflict(encrypt(DID));
        verify(identityRepository, never()).save(any());
        verify(userRepository, never()).updateProfile(any());
    }

    @Test
    void linkRejectsUserWhoAlreadyHasDadaeguIdentity() throws Exception {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.ACTIVE)));
        when(identityRepository.findByUserId(USER_ID))
            .thenReturn(List.of(existingIdentity()));

        assertLinkConflict(encrypt("did:daegu:another"));
        verify(identityRepository, never()).save(any());
    }

    @Test
    void linkFailsClosedWhenDidCiphertextIsBroken() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.link(USER_ID, "not-base64!", null, null, null))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(401);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.AUTHENTICATION_FAILED);
            });
        verify(identityRepository, never()).save(any());
    }

    @Test
    void resolveActiveUserIdReturnsUserBehindDadaeguDid() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.ACTIVE)));

        assertThat(service.resolveActiveUserId(encrypt(DID))).isEqualTo(USER_ID);

        verifyNoInteractions(tokenService, userRoleRepository);
        verify(userRepository, never()).updateProfile(any());
    }

    @Test
    void resolveActiveUserIdRejectsUnknownDidWith401AndNeverRegisters() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.empty());

        assertResolveFails(encrypt(DID));

        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
    }

    @Test
    void resolveActiveUserIdRejectsSuspendedUserAndBadCipherWith401() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.of(existingIdentity()));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.SUSPENDED)));

        assertResolveFails(encrypt(DID));
        assertResolveFails("not-base64!!");
        assertResolveFails(null);
    }

    private void assertResolveFails(String encryptedDid) {
        assertThatThrownBy(() -> service.resolveActiveUserId(encryptedDid))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(401);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.AUTHENTICATION_FAILED);
            });
    }

    private void assertLinkConflict(String encryptedDid) {
        assertThatThrownBy(() -> service.link(USER_ID, encryptedDid, null, null, null))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(409);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.IDENTITY_CONFLICT);
            });
    }

    private void assertAuthenticationFailed(
        String did, String name, String birthdate, String phone
    ) {
        assertThatThrownBy(() -> service.login(did, name, birthdate, phone))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(401);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.AUTHENTICATION_FAILED);
            });
    }

    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        return Base64.getEncoder().encodeToString(
            cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static UserIdentity existingIdentity() {
        return new UserIdentity(
            IDENTITY_ID, USER_ID, IdentityProvider.DADAEGU, DID, DID, NOW, null, NOW, NOW
        );
    }

    private static User userWithClaims(UserStatus status) {
        return new User(
            USER_ID, NAME, null, PHONE, LocalDate.of(1995, 12, 5),
            status, null, NOW, NOW
        );
    }

    private static UserRoleAssignment learner() {
        return new UserRoleAssignment(ROLE_ID, USER_ID, null, UserRole.LEARNER, NOW);
    }

    private static DadaeguRegistrationToken pendingToken() {
        return new DadaeguRegistrationToken(
            UUID.randomUUID(), OpaqueTokens.sha256Hex("raw-token"), DID, NAME, PHONE,
            LocalDate.of(1995, 12, 5), NOW.plusSeconds(600), NOW, NOW.minusSeconds(60));
    }
}
