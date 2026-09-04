package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.application.AuthenticatedUserContextFactory;
import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.user.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class JwtAuthenticationAdapter implements AuthenticationPort {

    private final JwtAuthenticationProperties properties;
    private final AuthenticatedUserContextFactory contextFactory;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationAdapter(
        JwtAuthenticationProperties properties,
        AuthenticatedUserContextFactory contextFactory,
        UserRepository userRepository,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.contextFactory = contextFactory;
        this.userRepository = userRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication authenticate(String token) {
        VerifiedClaims claims = verify(token);
        AuthenticatedUserContext context = contextFactory.create(
            claims.provider(),
            claims.providerSubject()
        );
        // 무효화 이력이 있으면 그 이후 발급된 토큰만 통과시킨다. 발급 시각(iat)이 없는 토큰은
        // 무효화 이후 발급됐음을 증명할 수 없으므로 거부한다.
        Instant invalidatedAt = userRepository.findAuthInvalidatedAt(context.userId()).orElse(null);
        if (invalidatedAt != null
            && (claims.issuedAt() == null || !claims.issuedAt().isAfter(invalidatedAt))) {
            throw authenticationFailed();
        }

        Set<String> roleNames = context.roles().stream()
            .map(role -> role.role())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
            context.userId(),
            context.provider(),
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
            JsonNode header = objectMapper.readTree(decodeText(parts[0]));
            JsonNode payload = objectMapper.readTree(decodeText(parts[1]));
            String algorithm = header.path("alg").asText("");
            String keyId = header.path("kid").asText("");
            if (!"HS256".equals(algorithm) || keyId.isBlank()) {
                throw authenticationFailed();
            }
            String encodedKey = properties.signingKeys().get(keyId);
            if (encodedKey == null || encodedKey.isBlank()) {
                throw authenticationFailed();
            }
            byte[] key = Base64.getDecoder().decode(encodedKey);
            byte[] expectedSignature = sign(
                parts[0] + "." + parts[1],
                key
            );
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw authenticationFailed();
            }

            if (!properties.issuer().equals(payload.path("iss").asText(""))) {
                throw authenticationFailed();
            }
            if (!hasAudience(payload.path("aud"), properties.audience())) {
                throw authenticationFailed();
            }
            JsonNode expiresAtNode = payload.path("exp");
            if (!expiresAtNode.isNumber()) {
                throw authenticationFailed();
            }
            Instant expiresAt = Instant.ofEpochSecond(expiresAtNode.longValue());
            if (!expiresAt.isAfter(clock.instant())) {
                throw authenticationFailed();
            }

            String providerSubject = payload.path("sub").asText("");
            String providerValue = payload.path("provider").asText("");
            if (providerSubject.isBlank() || providerValue.isBlank()) {
                throw authenticationFailed();
            }
            IdentityProvider provider = IdentityProvider.valueOf(providerValue);
            JsonNode issuedAtNode = payload.path("iat");
            Instant issuedAt = issuedAtNode.isNumber()
                ? Instant.ofEpochSecond(issuedAtNode.longValue()) : null;
            return new VerifiedClaims(provider, providerSubject, issuedAt, expiresAt);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw authenticationFailed();
        }
    }

    private String decodeText(String value) {
        return new String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
        );
    }

    private byte[] sign(String value, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean hasAudience(JsonNode audienceNode, String expected) {
        if (audienceNode.isTextual()) {
            return expected.equals(audienceNode.asText());
        }
        if (!audienceNode.isArray()) {
            return false;
        }
        for (JsonNode value : audienceNode) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private ApiException authenticationFailed() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTHENTICATION_FAILED,
            "Authentication failed"
        );
    }

    private record VerifiedClaims(
        IdentityProvider provider,
        String providerSubject,
        Instant issuedAt,
        Instant expiresAt
    ) {
    }
}
