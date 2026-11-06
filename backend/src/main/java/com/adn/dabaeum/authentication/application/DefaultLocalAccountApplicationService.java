package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.LoginAttemptRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.EmailAddress;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    private static final String EMAIL = "email";
    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final LocalAccountRepository localAccountRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SignupAgreements agreements;
    private final PasswordExpiryPolicy passwordExpiry;
    private final UserIdGenerator userIdGenerator;

    private final LocalAccessTokenService tokenService;
    private final LoginAttemptRepository loginAttempts;

    private final IdentityIdGenerator identityIdGenerator;
    private final RoleIdGenerator roleIdGenerator;
    private final PasswordEncoder passwordEncoder;

    private final Clock clock;
    private final String dummyPasswordHash;

    public DefaultLocalAccountApplicationService(
        LocalAccountRepository localAccountRepository,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository,
        UserAgreementRepository userAgreementRepository,
        UserIdGenerator userIdGenerator,
        IdentityIdGenerator identityIdGenerator,
        RoleIdGenerator roleIdGenerator,
        PasswordEncoder passwordEncoder,
        LocalAccessTokenService tokenService,
        LoginAttemptRepository loginAttempts,
        Clock clock
    ) {
        this.localAccountRepository = Objects.requireNonNull(localAccountRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.agreements = new SignupAgreements(Objects.requireNonNull(userAgreementRepository));
        this.userIdGenerator = Objects.requireNonNull(userIdGenerator);
        this.identityIdGenerator = Objects.requireNonNull(identityIdGenerator);
        this.roleIdGenerator = Objects.requireNonNull(roleIdGenerator);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.loginAttempts = Objects.requireNonNull(loginAttempts);
        this.clock = Objects.requireNonNull(clock);
        this.passwordExpiry = new PasswordExpiryPolicy(localAccountRepository, clock);
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

        String email = EmailAddress.deliverable(command.email())
            .orElseThrow(() -> validation(EMAIL));
        validatePassword(command.password());
        String name = normalizeRequired(command.name());
        String phone = normalizeNullable(command.phone(), "phone", 30);
        if (command.birthDate() != null
            && command.birthDate().isAfter(LocalDate.now(clock))) {
            throw validation("birthDate");
        }
        SignupAgreements.validate(command.agreedToUsage(), command.agreedToPrivacy());

        if (localAccountRepository.findByNormalizedEmail(email).isPresent()) {
            throw identityConflict();
        }

        Instant now = clock.instant();
        UUID userId = Objects.requireNonNull(
            userIdGenerator.generate(),
            "generated user id"
        );

        saveOrConflict(() -> userRepository.save(User.registered(
            userId, name, email, phone, command.birthDate(), now)));

        LocalAccountCredential credential = new LocalAccountCredential(
            Objects.requireNonNull(identityIdGenerator.generate(), "generated identity id"),
            userId,
            email,
            passwordEncoder.encode(command.password()),
            now,
            now
        );
        saveOrConflict(() -> localAccountRepository.save(credential));

        UserRoleAssignment learner = new UserRoleAssignment(
            Objects.requireNonNull(roleIdGenerator.generate(), "generated role id"),
            userId,
            null,
            UserRole.LEARNER,
            now
        );
        userRoleRepository.save(learner);
        agreements.record(userId, command.agreedToMarketing(), now);
        return result(userId, List.of(learner));
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public LocalAuthResult login(LoginCommand command) {
        if (command == null || command.password() == null) {
            throw authenticationFailed();
        }
        String email = EmailAddress.normalized(command.email())
            .orElseThrow(this::authenticationFailed);

        LocalAccountCredential credential = localAccountRepository
            .findByNormalizedEmail(email)
            .orElse(null);
        String hash = credential == null
            ? dummyPasswordHash
            : credential.passwordHash();
        // 잠겨 있어도 해시 비교는 한다 — 응답 시간으로 계정 존재를 가늠하지 못하게.
        boolean matches = passwordEncoder.matches(command.password(), hash);
        User user = credential == null
            ? null
            : userRepository.findById(credential.userId()).orElse(null);
        Instant now = clock.instant();
        if (credential != null && isLocked(credential.userId(), now)) {
            throw accountLocked();
        }
        if (credential != null && !matches) {
            // 실패 횟수 갱신은 401 을 던진 뒤에도 남아야 한다(noRollbackFor).
            boolean lockedNow = loginAttempts.recordFailure(
                credential.userId(), MAX_FAILED_LOGINS, now.plus(LOCK_DURATION));
            throw lockedNow ? accountLocked() : authenticationFailed();
        }
        if (credential == null || user == null || user.status() != UserStatus.ACTIVE) {
            throw authenticationFailed();
        }
        loginAttempts.clear(user.id());
        userRepository.recordLastLogin(user.id(), now);
        List<UserRoleAssignment> roles = userRoleRepository.findByUserId(user.id());
        return result(user.id(), roles).withPasswordExpired(passwordExpiry.isExpired(
            user.id(), roles.stream().map(role -> role.role().name()).toList()));
    }

    private boolean isLocked(UUID userId, Instant now) {
        return loginAttempts.findLockedUntil(userId)
            .map(until -> until.isAfter(now))
            .orElse(false);
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
        String email = EmailAddress.deliverable(command.email())
            .orElseThrow(() -> validation(EMAIL));
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
        saveOrConflict(() -> localAccountRepository.save(credential));
        userRepository.updateProfile(user.withProfile(
            user.name(), email, user.phone(), user.birthDate(),
            user.career(), user.introduction(), user.profileImageId(), now
        ));
        return credential;
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        if (command == null || command.userId() == null) {
            throw validation("userId");
        }
        validatePassword(command.newPassword(), "newPassword");
        LocalAccountCredential credential = localAccountRepository
            .findByUserId(command.userId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.IDENTITY_NOT_FOUND,
                "Local account not found"));
        if (!passwordEncoder.matches(
            command.currentPassword(), credential.passwordHash())) {
            throw authenticationFailed();
        }
        if (passwordEncoder.matches(
            command.newPassword(), credential.passwordHash())) {
            throw validation("newPassword");
        }
        Instant now = clock.instant();
        if (!localAccountRepository.updatePasswordHash(
            command.userId(), passwordEncoder.encode(command.newPassword()), now)) {
            throw new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.IDENTITY_NOT_FOUND,
                "Local account not found");
        }
        // 비밀번호가 바뀌면 이전 비밀번호로 얻은 토큰은 만료 전이라도 더 이상 유효하지 않아야 한다.
        userRepository.invalidateTokens(command.userId(), now);
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

    /**
     * 유니크 제약(이메일·사용자당 자격증명 1개)에 걸리면 "이미 있음"으로 알린다.
     */
    private void saveOrConflict(Runnable save) {
        try {
            save.run();
        } catch (DataIntegrityViolationException exception) {
            throw identityConflict();
        }
    }

    private void validatePassword(String value) {
        validatePassword(value, "password");
    }

    private void validatePassword(String value, String field) {
        if (value == null || value.length() < MIN_PASSWORD_LENGTH
            || value.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw validation(field);
        }
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeNullable(value, "name", 100);
        if (normalized == null) {
            throw validation("name");
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
            HttpStatus.UNPROCESSABLE_CONTENT,
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

    private ApiException accountLocked() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.ACCOUNT_LOCKED,
            "Account temporarily locked"
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
