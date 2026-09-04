package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class LocalJwtTokenService
    implements LocalAccessTokenService, AuthenticationPort {

    private static final String ALGORITHM = "HS256";
    private static final Set<String> PROVIDERS = Set.of("LOCAL", "DADAEGU");

    private final LocalJwtProperties properties;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final byte[] signingKey;

    public LocalJwtTokenService(
        LocalJwtProperties properties,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.clock = Objects.requireNonNull(clock);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.signingKey = properties.decodedSigningKey();
    }

    @Override
    public IssuedAccessToken issue(UUID userId, String provider) {
        Objects.requireNonNull(userId, "userId");
        if (provider == null || !PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("unsupported provider: " + provider);
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", ALGORITHM);
        header.put("typ", "JWT");
        header.put("kid", properties.keyId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", properties.issuer());
        payload.put("aud", properties.audience());
        payload.put("sub", userId.toString());
        payload.put("provider", provider);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        try {
            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String input = encodedHeader + "." + encodedPayload;
            return new IssuedAccessToken(
                input + "." + encode(sign(input)),
                expiresAt
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Access token generation failed", exception);
        }
    }

    @Override
    public Authentication authenticate(String token) {
        VerifiedClaims claims = verify(token);
        User user = userRepository.findById(claims.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            throw authenticationFailed();
        }
        // 비밀번호 재설정·계정 정지 이후 발급된 토큰만 통과시킨다. 만료 전 토큰의 즉시 무효화 수단이다.
        Instant invalidatedAt = userRepository.findAuthInvalidatedAt(user.id()).orElse(null);
        if (invalidatedAt != null && !claims.issuedAt().isAfter(invalidatedAt)) {
            throw authenticationFailed();
        }
        Set<AuthenticatedRole> roles = userRoleRepository.findByUserId(user.id())
            .stream()
            .map(role -> new AuthenticatedRole(
                role.role().name(),
                role.institutionId()
            ))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            user.id(),
            claims.provider(),
            roles
        );
        Set<String> roleNames = roles.stream()
            .map(AuthenticatedRole::role)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
            user.id(),
            claims.provider(),
            roleNames
        );
        Set<SimpleGrantedAuthority> authorities = roleNames.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                authorities
            );
        authentication.setDetails(new AuthenticatedTokenDetails(
            claims.expiresAt(),
            context
        ));
        return authentication;
    }

    private VerifiedClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw authenticationFailed();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw authenticationFailed();
        }
        try {
            JsonNode header = objectMapper.readTree(decode(parts[0]));
            JsonNode payload = objectMapper.readTree(decode(parts[1]));
            if (!ALGORITHM.equals(header.path("alg").asText(""))
                || !"JWT".equals(header.path("typ").asText(""))
                || !properties.keyId().equals(header.path("kid").asText(""))) {
                throw authenticationFailed();
            }
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw authenticationFailed();
            }
            if (!properties.issuer().equals(payload.path("iss").asText(""))
                || !hasAudience(payload.path("aud"), properties.audience())
                || !PROVIDERS.contains(payload.path("provider").asText(""))
                || !payload.path("iat").isNumber()
                || !payload.path("exp").isNumber()) {
                throw authenticationFailed();
            }
            Instant issuedAt = Instant.ofEpochSecond(payload.path("iat").longValue());
            Instant expiresAt = Instant.ofEpochSecond(payload.path("exp").longValue());
            Instant now = clock.instant();
            if (issuedAt.isAfter(now)
                || !expiresAt.isAfter(issuedAt)
                || expiresAt.isAfter(issuedAt.plus(properties.accessTokenTtl()))
                || !expiresAt.isAfter(now)) {
                throw authenticationFailed();
            }
            UUID userId = UUID.fromString(payload.path("sub").asText(""));
            return new VerifiedClaims(
                userId,
                payload.path("provider").asText(),
                issuedAt,
                expiresAt
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw authenticationFailed();
        }
    }

    private boolean hasAudience(JsonNode node, String expected) {
        if (node.isTextual()) {
            return expected.equals(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                if (expected.equals(value.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private ApiException authenticationFailed() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTHENTICATION_FAILED,
            "Authentication failed"
        );
    }

    private record VerifiedClaims(
        UUID userId, String provider, Instant issuedAt, Instant expiresAt
    ) {
    }
}
