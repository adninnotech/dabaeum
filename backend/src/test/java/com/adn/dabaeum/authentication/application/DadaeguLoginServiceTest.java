package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.adn.dabaeum.user.domain.User;
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
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void firstLoginRegistersUserWithDecryptedClaimsAndIssuesDadaeguToken() throws Exception {
        when(identityRepository.findByProviderSubject(IdentityProvider.DADAEGU, DID))
            .thenReturn(Optional.empty());
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);
        when(roleIdGenerator.generate()).thenReturn(ROLE_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(userWithClaims(UserStatus.ACTIVE)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(learner()));
        when(tokenService.issue(USER_ID, "DADAEGU")).thenReturn(new IssuedAccessToken(
            "signed-access-token",
            NOW.plusSeconds(3600)
        ));

        LocalAuthResult result = service.login(
            encrypt(DID), encrypt(NAME), encrypt(BIRTHDATE), encrypt(PHONE)
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.id()).isEqualTo(USER_ID);
        assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.name()).isEqualTo(NAME);
        assertThat(saved.phone()).isEqualTo(PHONE);
        assertThat(saved.birthDate()).isEqualTo(LocalDate.of(1995, 12, 5));
        assertThat(saved.email()).isNull();

        ArgumentCaptor<UserIdentity> identityCaptor =
            ArgumentCaptor.forClass(UserIdentity.class);
        verify(identityRepository).save(identityCaptor.capture());
        UserIdentity identity = identityCaptor.getValue();
        assertThat(identity.provider()).isEqualTo(IdentityProvider.DADAEGU);
        assertThat(identity.providerSubject()).isEqualTo(DID);
        assertThat(identity.externalDid()).isEqualTo(DID);
        assertThat(identity.verifiedAt()).isEqualTo(NOW);

        verify(userRoleRepository).save(learner());
        // 저장된 값이 다대구 값과 같으므로 갱신 UPDATE 는 없다
        verify(userRepository, never()).updateProfile(any());

        assertThat(result.provider()).isEqualTo("DADAEGU");
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.roles()).extracting(role -> role.role())
            .containsExactly("LEARNER");
    }

    @Test
    void repeatedLoginRefreshesChangedProfileFromDadaegu() throws Exception {
        User existing = new User(
            USER_ID, "개명전이름", "keep@example.com", "01000000000",
            LocalDate.of(1995, 12, 5), UserStatus.ACTIVE, null, NOW, NOW,
            "경력", "소개", null
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

        LocalAuthResult result = service.login(encrypt(DID), null, null, null);

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
}
