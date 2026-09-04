package com.adn.dabaeum.authentication.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.application.IssuedAccessToken;
import com.adn.dabaeum.authentication.application.LocalJwtProperties;
import com.adn.dabaeum.authentication.application.LocalJwtTokenService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LocalJwtTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");
    private static final byte[] KEY =
        "dabaeum-local-jwt-test-key-material-32".getBytes(StandardCharsets.UTF_8);

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;

    private ObjectMapper objectMapper;
    private LocalJwtTokenService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = serviceAt(NOW, KEY);
    }

    @Test
    void issuesMinimalHs256AccessToken() throws Exception {
        IssuedAccessToken issued = service.issue(USER_ID);
        String[] parts = issued.value().split("\\.");

        assertThat(parts).hasSize(3);
        JsonNode header = decode(parts[0]);
        JsonNode payload = decode(parts[1]);
        assertThat(header.path("alg").asText()).isEqualTo("HS256");
        assertThat(header.path("typ").asText()).isEqualTo("JWT");
        assertThat(header.path("kid").asText()).isEqualTo("local-development-1");
        assertThat(payload.path("iss").asText()).isEqualTo("dabaeum-local");
        assertThat(payload.path("aud").asText()).isEqualTo("dabaeum-api");
        assertThat(payload.path("sub").asText()).isEqualTo(USER_ID.toString());
        assertThat(payload.path("provider").asText()).isEqualTo("LOCAL");
        assertThat(payload.path("iat").longValue()).isEqualTo(NOW.getEpochSecond());
        assertThat(payload.path("exp").longValue())
            .isEqualTo(NOW.plusSeconds(3600).getEpochSecond());
        assertThat(payload.has("email")).isFalse();
        assertThat(payload.has("roles")).isFalse();
        assertThat(payload.has("name")).isFalse();
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void authenticatesActiveUserWithCurrentDatabaseRoles() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            new UserRoleAssignment(
                ROLE_ID,
                USER_ID,
                null,
                UserRole.LEARNER,
                NOW
            )
        ));

        IssuedAccessToken issued = service.issue(USER_ID);
        Authentication authentication = service.authenticate(issued.value());

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_LEARNER");
        assertThat(authentication.getPrincipal())
            .isInstanceOfSatisfying(AuthenticatedUserPrincipal.class, principal -> {
                assertThat(principal.userId()).isEqualTo(USER_ID);
                assertThat(principal.provider()).isEqualTo("LOCAL");
            });
        assertThat(authentication.getDetails())
            .isInstanceOf(AuthenticatedTokenDetails.class);
    }

    @Test
    void issuesAndAuthenticatesDadaeguProviderToken() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        IssuedAccessToken issued = service.issue(USER_ID, "DADAEGU");
        JsonNode payload = decode(issued.value().split("\\.")[1]);
        assertThat(payload.path("provider").asText()).isEqualTo("DADAEGU");
        assertThat(payload.path("sub").asText()).isEqualTo(USER_ID.toString());

        Authentication authentication = service.authenticate(issued.value());
        assertThat(authentication.getPrincipal())
            .isInstanceOfSatisfying(AuthenticatedUserPrincipal.class, principal ->
                assertThat(principal.provider()).isEqualTo("DADAEGU"));
        assertThat(authentication.getDetails())
            .isInstanceOfSatisfying(AuthenticatedTokenDetails.class, details ->
                assertThat(details.context().provider()).isEqualTo("DADAEGU"));
    }

    @Test
    void rejectsUnsupportedProvider() throws Exception {
        assertThatThrownBy(() -> service.issue(USER_ID, "GOOGLE"))
            .isInstanceOf(IllegalArgumentException.class);
        assertAuthenticationFailed(signedToken(NOW, NOW.plusSeconds(3600), "DID"));
    }

    @Test
    void rejectsTamperedExpiredAndInactiveTokens() {
        String token = service.issue(USER_ID).value();
        assertAuthenticationFailed(token.substring(0, token.length() - 1) + "A");

        LocalJwtTokenService later = serviceAt(NOW.plusSeconds(3601), KEY);
        assertThatThrownBy(() -> later.authenticate(token))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(
                    ApiErrorCode.AUTHENTICATION_FAILED
                ));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(
            USER_ID,
            "정지 사용자",
            "learner@example.com",
            null,
            null,
            UserStatus.SUSPENDED,
            null,
            NOW,
            NOW
        )));
        assertAuthenticationFailed(token);
    }

    @Test
    void rejectsFutureReversedAndOverlongTimeClaims() throws Exception {
        assertAuthenticationFailed(signedToken(
            NOW.plusSeconds(1),
            NOW.plusSeconds(3601)
        ));
        assertAuthenticationFailed(signedToken(
            NOW.minusSeconds(10),
            NOW.minusSeconds(10)
        ));
        assertAuthenticationFailed(signedToken(
            NOW.minusSeconds(1),
            NOW.plusSeconds(3600)
        ));
    }

    // 비밀번호 재설정·계정 정지 시 auth_invalidated_at 이 갱신되고, 그보다 먼저 발급된 토큰은
    // 만료 전이라도 거부돼야 한다. 무효화 이후 발급된 토큰만 통과한다.
    @Test
    void rejectsTokenIssuedAtOrBeforeCredentialInvalidation() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userRepository.findAuthInvalidatedAt(USER_ID))
            .thenReturn(Optional.of(NOW.minusSeconds(30)));

        assertAuthenticationFailed(signedToken(NOW.minusSeconds(60), NOW.plusSeconds(3000)));
        assertAuthenticationFailed(signedToken(NOW.minusSeconds(30), NOW.plusSeconds(3000)));
    }

    @Test
    void acceptsTokenIssuedAfterCredentialInvalidation() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(userRepository.findAuthInvalidatedAt(USER_ID))
            .thenReturn(Optional.of(NOW.minusSeconds(30)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.authenticate(signedToken(NOW.minusSeconds(10), NOW.plusSeconds(3000)))
            .isAuthenticated()).isTrue();
    }

    private LocalJwtTokenService serviceAt(Instant instant, byte[] key) {
        return new LocalJwtTokenService(
            new LocalJwtProperties(
                "dabaeum-local",
                "dabaeum-api",
                "local-development-1",
                Base64.getEncoder().encodeToString(key),
                Duration.ofHours(1)
            ),
            userRepository,
            userRoleRepository,
            Clock.fixed(instant, ZoneOffset.UTC),
            objectMapper == null ? new ObjectMapper() : objectMapper
        );
    }

    private JsonNode decode(String value) throws Exception {
        return objectMapper.readTree(new String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
        ));
    }

    private String signedToken(Instant issuedAt, Instant expiresAt)
        throws Exception {
        return signedToken(issuedAt, expiresAt, "LOCAL");
    }

    private String signedToken(Instant issuedAt, Instant expiresAt, String provider)
        throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        header.put("kid", "local-development-1");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "dabaeum-local");
        payload.put("aud", "dabaeum-api");
        payload.put("sub", USER_ID.toString());
        payload.put("provider", provider);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(header));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(payload));
        String input = encodedHeader + "." + encodedPayload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
        return input + "." + signature;
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

    private void assertAuthenticationFailed(String token) {
        assertThatThrownBy(() -> service.authenticate(token))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(401);
                assertThat(exception.code()).isEqualTo(
                    ApiErrorCode.AUTHENTICATION_FAILED
                );
                assertThat(exception.getMessage()).doesNotContain(token);
            });
    }
}
