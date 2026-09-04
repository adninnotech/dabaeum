package com.adn.dabaeum.authentication.application;

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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultLocalAccountApplicationService
    implements LocalAccountApplicationService {

    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String PROVIDER = "LOCAL";

    private final LocalAccountRepository localAccountRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserIdGenerator userIdGenerator;
    private final IdentityIdGenerator identityIdGenerator;
    private final RoleIdGenerator roleIdGenerator;
    private final PasswordEncoder passwordEncoder;
    private final LocalAccessTokenService tokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public DefaultLocalAccountApplicationService(
        LocalAccountRepository localAccountRepository,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository,
        UserIdGenerator userIdGenerator,
        IdentityIdGenerator identityIdGenerator,
        RoleIdGenerator roleIdGenerator,
        PasswordEncoder passwordEncoder,
        LocalAccessTokenService tokenService,
        Clock clock
    ) {
        this.localAccountRepository = Objects.requireNonNull(localAccountRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.userIdGenerator = Objects.requireNonNull(userIdGenerator);
        this.identityIdGenerator = Objects.requireNonNull(identityIdGenerator);
        this.roleIdGenerator = Objects.requireNonNull(roleIdGenerator);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.clock = Objects.requireNonNull(clock);
        this.dummyPasswordHash = passwordEncoder.encode(
            "dabaeum-authentication-dummy-password"
        );
    }

    @Override
    @Transactional
    public LocalAuthResult signup(SignupCommand command) {
        if (command == null) {
            throw validation("requestBody");
        }
        String email = normalizeEmail(command.email());
        validatePassword(command.password());
        String name = normalizeRequired(command.name(), "name", 100);
        String phone = normalizeNullable(command.phone(), "phone", 30);
        if (command.birthDate() != null
            && command.birthDate().isAfter(LocalDate.now(clock))) {
            throw validation("birthDate");
        }
        if (localAccountRepository.findByNormalizedEmail(email).isPresent()) {
            throw identityConflict();
        }

        Instant now = clock.instant();
        UUID userId = Objects.requireNonNull(
            userIdGenerator.generate(),
            "generated user id"
        );
        try {
            userRepository.save(new User(
                userId,
                name,
                email,
                phone,
                command.birthDate(),
                UserStatus.ACTIVE,
                null,
                now,
                now
            ));
        } catch (DataIntegrityViolationException exception) {
            throw identityConflict();
        }

        LocalAccountCredential credential = new LocalAccountCredential(
            Objects.requireNonNull(identityIdGenerator.generate(), "generated identity id"),
            userId,
            email,
            passwordEncoder.encode(command.password()),
            now,
            now
        );
        try {
            localAccountRepository.save(credential);
        } catch (DataIntegrityViolationException exception) {
            throw identityConflict();
        }

        UserRoleAssignment learner = new UserRoleAssignment(
            Objects.requireNonNull(roleIdGenerator.generate(), "generated role id"),
            userId,
            null,
            UserRole.LEARNER,
            now
        );
        userRoleRepository.save(learner);
        return result(userId, List.of(learner));
    }

    @Override
    @Transactional(readOnly = true)
    public LocalAuthResult login(LoginCommand command) {
        if (command == null || command.password() == null) {
            throw authenticationFailed();
        }
        String email;
        try {
            email = normalizeEmail(command.email());
        } catch (ApiException exception) {
            throw authenticationFailed();
        }

        LocalAccountCredential credential = localAccountRepository
            .findByNormalizedEmail(email)
            .orElse(null);
        String hash = credential == null
            ? dummyPasswordHash
            : credential.passwordHash();
        boolean matches = passwordEncoder.matches(command.password(), hash);
        User user = credential == null
            ? null
            : userRepository.findById(credential.userId()).orElse(null);
        if (credential == null || !matches || user == null
            || user.status() != UserStatus.ACTIVE) {
            throw authenticationFailed();
        }
        return result(user.id(), userRoleRepository.findByUserId(user.id()));
    }

    @Override
    @Transactional
    public LocalAccountCredential linkLocal(LinkLocalAccountCommand command) {
        if (command == null || command.userId() == null) {
            throw validation("userId");
        }
        User user = userRepository.findById(command.userId())
            .filter(found -> found.status() == UserStatus.ACTIVE)
            .orElseThrow(this::authenticationFailed);
        String email = normalizeEmail(command.email());
        validatePassword(command.password());
        if (localAccountRepository.findByUserId(user.id()).isPresent()
            || localAccountRepository.findByNormalizedEmail(email).isPresent()) {
            throw identityConflict();
        }

        Instant now = clock.instant();
        LocalAccountCredential credential = new LocalAccountCredential(
            Objects.requireNonNull(identityIdGenerator.generate(), "generated identity id"),
            user.id(),
            email,
            passwordEncoder.encode(command.password()),
            now,
            now
        );
        try {
            localAccountRepository.save(credential);
        } catch (DataIntegrityViolationException exception) {
            throw identityConflict();
        }
        userRepository.updateProfile(new User(
            user.id(), user.name(), email, user.phone(), user.birthDate(),
            user.status(), user.withdrawnAt(), user.createdAt(), now,
            user.career(), user.introduction(), user.profileImageId()
        ));
        return credential;
    }

    private LocalAuthResult result(
        UUID userId,
        List<UserRoleAssignment> assignments
    ) {
        return LocalAuthResult.of(
            tokenService.issue(userId),
            userId,
            PROVIDER,
            assignments,
            clock
        );
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

    private void validatePassword(String value) {
        if (value == null || value.length() < MIN_PASSWORD_LENGTH
            || value.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw validation("password");
        }
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = normalizeNullable(value, field, maxLength);
        if (normalized == null) {
            throw validation(field);
        }
        return normalized;
    }

    private String normalizeNullable(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw validation(field);
        }
        return normalized;
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field)
        );
    }

    private ApiException identityConflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.IDENTITY_CONFLICT,
            "Identity already exists"
        );
    }

    private ApiException authenticationFailed() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTHENTICATION_FAILED,
            "Authentication failed"
        );
    }
}
