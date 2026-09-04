package com.adn.dabaeum.authentication.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.application.AuthenticatedUserContextFactory;
import com.adn.dabaeum.authentication.application.JwtAuthenticationAdapter;
import com.adn.dabaeum.authentication.application.JwtAuthenticationProperties;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.user.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final byte[] SIGNING_KEY =
        "stage3-fake-signing-key-material".getBytes(StandardCharsets.UTF_8);
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );

    @Mock
    AuthenticatedUserContextFactory contextFactory;

    @Mock
    UserRepository userRepository;

    private JwtAuthenticationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JwtAuthenticationAdapter(
            new JwtAuthenticationProperties(
                "https://issuer.example.test",
                "dabaeum-web",
                Map.of(
                    "key-1",
                    Base64.getEncoder().encodeToString(SIGNING_KEY)
                )
            ),
            contextFactory,
            userRepository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new ObjectMapper()
        );
    }

    @Test
    void verifiesClaimsMapsIdentityAndProducesSpringAuthorities() {
        Instant expiresAt = NOW.plusSeconds(900);
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            USER_ID,
            "DADAEGU",
            Set.of(new AuthenticatedRole("INSTRUCTOR", UUID.fromString(
                "22222222-2222-2222-2222-222222222222"
            )))
        );
        when(contextFactory.create(
            IdentityProvider.DADAEGU,
            "provider-subject"
        )).thenReturn(context);

        Authentication authentication = adapter.authenticate(token(
            "key-1",
            "provider-subject",
            "DADAEGU",
            expiresAt,
            "https://issuer.example.test",
            "dabaeum-web",
            SIGNING_KEY
        ));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_INSTRUCTOR");
        assertThat(authentication.getPrincipal())
            .isInstanceOf(AuthenticatedUserPrincipal.class);
        AuthenticatedUserPrincipal principal =
            (AuthenticatedUserPrincipal) authentication.getPrincipal();
        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.provider()).isEqualTo("DADAEGU");
        assertThat(authentication.getDetails())
            .isEqualTo(new AuthenticatedTokenDetails(expiresAt, context));

        verify(contextFactory).create(
            IdentityProvider.DADAEGU,
            "provider-subject"
        );
    }

    @Test
    void rejectsExpiredTokenBeforeIdentityLookup() {
        assertFailure(ApiErrorCode.AUTHENTICATION_FAILED, token(
            "key-1",
            "provider-subject",
            "DADAEGU",
            NOW.minusSeconds(1),
            "https://issuer.example.test",
            "dabaeum-web",
            SIGNING_KEY
        ));

        verify(contextFactory, never()).create(
            IdentityProvider.DADAEGU,
            "provider-subject"
        );
    }

    @Test
    void rejectsWrongIssuerAndAudience() {
        assertFailure(ApiErrorCode.AUTHENTICATION_FAILED, token(
            "key-1", "provider-subject", "DADAEGU", NOW.plusSeconds(60),
            "https://another-issuer.example.test", "dabaeum-web", SIGNING_KEY
        ));
        assertFailure(ApiErrorCode.AUTHENTICATION_FAILED, token(
            "key-1", "provider-subject", "DADAEGU", NOW.plusSeconds(60),
            "https://issuer.example.test", "another-audience", SIGNING_KEY
        ));
    }

    @Test
    void rejectsBadSignatureAndUnknownKeyId() {
        assertFailure(ApiErrorCode.AUTHENTICATION_FAILED, token(
            "key-1", "provider-subject", "DADAEGU", NOW.plusSeconds(60),
            "https://issuer.example.test", "dabaeum-web",
            "a-different-signing-key".getBytes(StandardCharsets.UTF_8)
        ));
        assertFailure(ApiErrorCode.AUTHENTICATION_FAILED, token(
            "unknown-key", "provider-subject", "DADAEGU", NOW.plusSeconds(60),
            "https://issuer.example.test", "dabaeum-web", SIGNING_KEY
        ));
    }

    @Test
    void returnsIdentityNotFoundWithoutLeakingToken() {
        when(contextFactory.create(
            IdentityProvider.DADAEGU,
            "provider-subject"
        )).thenThrow(new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.IDENTITY_NOT_FOUND,
            "Identity not found"
        ));

        assertThatThrownBy(() -> adapter.authenticate(token(
            "key-1", "provider-subject", "DADAEGU", NOW.plusSeconds(60),
            "https://issuer.example.test", "dabaeum-web", SIGNING_KEY
        ))).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ApiErrorCode.IDENTITY_NOT_FOUND);
            assertThat(exception.getMessage()).doesNotContain("provider-subject");
        });
    }

    private void assertFailure(ApiErrorCode code, String token) {
        assertThatThrownBy(() -> adapter.authenticate(token))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(code);
                assertThat(exception.getMessage()).doesNotContain(token);
            });
    }

    private String token(
        String keyId,
        String subject,
        String provider,
        Instant expiresAt,
        String issuer,
        String audience,
        byte[] signingKey
    ) {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\""
            + keyId + "\"}";
        String payload = "{\"iss\":\"" + issuer + "\",\"aud\":\""
            + audience + "\",\"sub\":\"" + subject
            + "\",\"provider\":\"" + provider + "\",\"exp\":"
            + expiresAt.getEpochSecond() + "}";
        String encodedHeader = encode(header);
        String encodedPayload = encode(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + encodeBytes(sign(signingInput, signingKey));
    }

    private String encode(String value) {
        return encodeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeBytes(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] sign(String value, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new AssertionError(exception);
        }
    }
}
