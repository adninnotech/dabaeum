package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.application.port.PasswordResetMailSender;
import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.PasswordResetToken;
import com.adn.dabaeum.authentication.domain.PasswordResetTokenRepository;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.adn.dabaeum.user.domain.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultAccountRecoveryService implements AccountRecoveryService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final char[] TEMP_PASSWORD_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$"
            .toCharArray();

    private final LocalAccountRepository localAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationPolicy authorizationPolicy;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultAccountRecoveryService(
        LocalAccountRepository localAccountRepository,
        PasswordResetTokenRepository tokenRepository,
        PasswordResetMailSender mailSender,
        PasswordEncoder passwordEncoder,
        AuthorizationPolicy authorizationPolicy,
        UserRepository userRepository,
        Clock clock
    ) {
        this.localAccountRepository = localAccountRepository;
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.authorizationPolicy = authorizationPolicy;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        String normalized = normalizeEmail(email);
        return localAccountRepository.findByNormalizedEmail(normalized).isEmpty();
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = normalizeEmail(email);
        Optional<LocalAccountCredential> account =
            localAccountRepository.findByNormalizedEmail(normalized);
        if (account.isEmpty()) {
            // 계정 존재 여부를 응답으로 노출하지 않는다.
            return;
        }
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tokenBytes);
        Instant now = clock.instant();
        tokenRepository.save(new PasswordResetToken(
            UUID.randomUUID(), account.get().userId(), sha256Hex(rawToken),
            now.plus(TOKEN_TTL), null, now));
        mailSender.sendResetLink(normalized, rawToken);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(String token, String password) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        validatePassword(password);
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository
            .findByTokenHash(sha256Hex(token.trim()))
            .filter(found -> found.isUsable(now))
            .orElseThrow(this::invalidToken);
        if (!tokenRepository.markUsed(resetToken.id(), now)) {
            throw invalidToken();
        }
        if (!localAccountRepository.updatePasswordHash(
            resetToken.userId(), passwordEncoder.encode(password), now)) {
            throw invalidToken();
        }
        // 비밀번호가 바뀌면 이전 비밀번호로 얻은 토큰은 만료 전이라도 더 이상 유효하지 않아야 한다.
        userRepository.invalidateTokens(resetToken.userId(), now);
    }

    @Override
    @Transactional
    public AdminResetResult adminResetPassword(
        AuthenticatedUserContext actor, UUID userId, String temporaryPassword
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        Objects.requireNonNull(userId, "userId");
        localAccountRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND,
                "Local account not found"));
        String password = temporaryPassword == null || temporaryPassword.isBlank()
            ? generateTemporaryPassword()
            : temporaryPassword.trim();
        validatePassword(password);
        Instant now = clock.instant();
        if (!localAccountRepository.updatePasswordHash(
            userId, passwordEncoder.encode(password), now)) {
            throw new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND,
                "Local account not found");
        }
        userRepository.invalidateTokens(userId, now);
        return new AdminResetResult(now, password);
    }

    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(14);
        for (int index = 0; index < 14; index++) {
            builder.append(TEMP_PASSWORD_ALPHABET[
                secureRandom.nextInt(TEMP_PASSWORD_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private void validatePassword(String password) {
        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH
            || password.length() > 128) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("password"));
        }
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            throw validation("email");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (normalized.isBlank() || normalized.length() > 320
            || at < 1 || at == normalized.length() - 1
            || normalized.indexOf('@', at + 1) >= 0) {
            throw validation("email");
        }
        return normalized;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ApiException invalidToken() {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.PASSWORD_RESET_TOKEN_INVALID,
            "Password reset token is invalid or expired",
            List.of("token"));
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
