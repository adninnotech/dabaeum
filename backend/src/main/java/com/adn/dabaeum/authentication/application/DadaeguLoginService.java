package com.adn.dabaeum.authentication.application;

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
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Cipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다대구 QR 로그인(requiredVC=Name:PhoneNum:Birthdate). 암호화된 DID·이름·생년월일·전화번호를
 * 복호화한다. 재로그인이면 프로필을 다대구 값으로 갱신하고 토큰을 준다. 처음 보는 DID 면 계정을 만들지
 * 않고 1회용 가입 토큰을 준다 — 관심 분야·약관을 받는 두 번째 단계(signup)에서 계정이 생긴다
 * (설계서 §4.2). ci·성별·내외국인은 받지도 저장하지도 않는다.
 */
@Service
@Profile({"local", "dev"})
@EnableConfigurationProperties(DadaeguLoginProperties.class)
public class DadaeguLoginService {

    private static final IdentityProvider PROVIDER = IdentityProvider.DADAEGU;
    private static final String CIPHER = "RSA/ECB/PKCS1Padding";
    private static final Duration REGISTRATION_TOKEN_TTL = Duration.ofMinutes(10);

    private final PrivateKey privateKey;
    private final UserIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserIdGenerator userIdGenerator;
    private final IdentityIdGenerator identityIdGenerator;
    private final RoleIdGenerator roleIdGenerator;
    private final LocalAccessTokenService tokenService;
    private final DadaeguRegistrationTokenRepository registrationTokenRepository;
    private final SignupAgreements agreements;
    private final UserInterestService interests;
    private final Clock clock;

    public DadaeguLoginService(
        DadaeguLoginProperties properties,
        UserIdentityRepository identityRepository,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository,
        UserIdGenerator userIdGenerator,
        IdentityIdGenerator identityIdGenerator,
        RoleIdGenerator roleIdGenerator,
        LocalAccessTokenService tokenService,
        DadaeguRegistrationTokenRepository registrationTokenRepository,
        UserAgreementRepository userAgreementRepository,
        UserInterestService interests,
        Clock clock
    ) {
        this.privateKey = Objects.requireNonNull(properties).rsaPrivateKey();
        this.identityRepository = Objects.requireNonNull(identityRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.userIdGenerator = Objects.requireNonNull(userIdGenerator);
        this.identityIdGenerator = Objects.requireNonNull(identityIdGenerator);
        this.roleIdGenerator = Objects.requireNonNull(roleIdGenerator);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.registrationTokenRepository = Objects.requireNonNull(registrationTokenRepository);
        this.agreements = new SignupAgreements(Objects.requireNonNull(userAgreementRepository));
        this.interests = Objects.requireNonNull(interests);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public DadaeguLoginOutcome login(
        String encryptedDid,
        String encryptedName,
        String encryptedBirthdate,
        String encryptedPhone
    ) {
        String did = decryptDid(encryptedDid);
        String name = claim(encryptedName, 100);
        LocalDate birthDate = birthdateClaim(encryptedBirthdate);
        String phone = claim(encryptedPhone, 30);
        Instant now = clock.instant();
        Optional<UserIdentity> identity = identityRepository.findByProviderSubject(PROVIDER, did);
        if (identity.isEmpty()) {
            return requireRegistration(did, name, phone, birthDate, now);
        }
        User user = userRepository.findById(identity.get().userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            throw authenticationFailed();
        }
        refreshProfile(user, name, phone, birthDate, now);
        userRepository.recordLastLogin(user.id(), now);
        return new DadaeguLoginOutcome.LoggedIn(LocalAuthResult.of(
            tokenService.issue(user.id(), PROVIDER.name()),
            user.id(),
            PROVIDER.name(),
            userRoleRepository.findByUserId(user.id()),
            clock
        ));
    }

    /**
     * 가입 두 번째 단계. 값 검증 → 토큰 원자 소비 → 계정·identity·역할·약관을 한 트랜잭션에.
     * 토큰은 검증이 끝난 뒤에만 소비한다 — 잘못된 요청으로 토큰이 날아가지 않게.
     */
    @Transactional
    public LocalAuthResult signup(DadaeguSignupCommand command) {
        if (command == null) {
            throw authenticationFailed();
        }
        interests.validateForSignup(command.interestCodes(), command.agreedToInterest());
        SignupAgreements.validate(command.agreedToUsage(), command.agreedToPrivacy());
        if (command.registrationToken() == null || command.registrationToken().isBlank()) {
            throw authenticationFailed();
        }
        Instant now = clock.instant();
        DadaeguRegistrationToken token = registrationTokenRepository
            .consume(OpaqueTokens.sha256Hex(command.registrationToken().trim()), now)
            .orElseThrow(this::authenticationFailed);
        if (identityRepository.findByProviderSubject(PROVIDER, token.did()).isPresent()) {
            throw identityConflict();
        }

        UUID userId = Objects.requireNonNull(userIdGenerator.generate(), "generated user id");
        UserIdentity identity = new UserIdentity(
            Objects.requireNonNull(identityIdGenerator.generate(), "generated identity id"),
            userId, PROVIDER, token.did(), token.did(), now, null, now, now);
        try {
            userRepository.save(User.registered(
                userId, token.name(), null, token.phone(), token.birthDate(), now));
            identityRepository.save(identity);
        } catch (DataIntegrityViolationException exception) {
            // 같은 DID 동시 가입 — 트랜잭션이 이미 깨졌으니 409 로 돌려보낸다
            throw identityConflict();
        }
        UserRoleAssignment learner = new UserRoleAssignment(
            Objects.requireNonNull(roleIdGenerator.generate(), "generated role id"),
            userId, null, UserRole.LEARNER, now);
        userRoleRepository.save(learner);
        agreements.record(userId, command.agreedToMarketing(), now);
        interests.recordAtSignup(userId, command.interestCodes(), command.agreedToInterest(), now);
        userRepository.recordLastLogin(userId, now);
        return LocalAuthResult.of(
            tokenService.issue(userId, PROVIDER.name()), userId, PROVIDER.name(), List.of(learner),
            clock);
    }

    /**
     * 로그인된 사용자에게 다대구 DID 를 붙인다(일반회원 → 통합). 로그인과 같은 복호화를 쓰고,
     * 이름·전화·생년월일은 다대구 값으로 덮어쓴다. DID 가 이미 누구에게든 묶였거나
     * 이 사용자에게 DADAEGU identity 가 이미 있으면 409.
     */
    @Transactional
    public UserIdentity link(
        UUID userId,
        String encryptedDid,
        String encryptedName,
        String encryptedBirthdate,
        String encryptedPhone
    ) {
        User user = userRepository.findById(Objects.requireNonNull(userId, "userId"))
            .filter(found -> found.status() == UserStatus.ACTIVE)
            .orElseThrow(this::authenticationFailed);
        String did = decryptDid(encryptedDid);
        String name = claim(encryptedName, 100);
        LocalDate birthDate = birthdateClaim(encryptedBirthdate);
        String phone = claim(encryptedPhone, 30);
        boolean alreadyLinked = identityRepository.findByUserId(userId).stream()
            .anyMatch(identity -> identity.provider() == PROVIDER);
        if (alreadyLinked
            || identityRepository.findByProviderSubject(PROVIDER, did).isPresent()) {
            throw identityConflict();
        }
        Instant now = clock.instant();
        UserIdentity identity = new UserIdentity(
            Objects.requireNonNull(identityIdGenerator.generate(), "generated identity id"),
            userId, PROVIDER, did, did, now, null, now, now
        );
        try {
            identityRepository.save(identity);
        } catch (DataIntegrityViolationException exception) {
            throw identityConflict();
        }
        refreshProfile(user, name, phone, birthDate, now);
        return identity;
    }

    /**
     * 출석처럼 "이미 가입된 다대구 사용자인지"만 확인하는 경로(requiredVC=OnlyDid).
     * 로그인과 같은 복호화를 쓰되 자동 가입·프로필 갱신·토큰 발급은 하지 않는다.
     * 모르는 DID·비활성 사용자·복호화 실패는 모두 같은 401.
     */
    @Transactional(readOnly = true)
    public UUID resolveActiveUserId(String encryptedDid) {
        String did = decryptDid(encryptedDid);
        UserIdentity identity = identityRepository.findByProviderSubject(PROVIDER, did)
            .orElseThrow(this::authenticationFailed);
        User user = userRepository.findById(identity.userId())
            .filter(found -> found.status() == UserStatus.ACTIVE)
            .orElseThrow(this::authenticationFailed);
        return user.id();
    }

    private String decryptDid(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            throw authenticationFailed();
        }
        String did = decryptValue(encrypted);
        if (!did.startsWith("did:") || did.length() > 512) {
            throw authenticationFailed();
        }
        return did;
    }

    /** 선택 클레임: 안 왔으면 null, 왔는데 깨졌으면 401 — 조용히 버리지 않는다. */
    private String claim(String encrypted, int maxLength) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        String value = decryptValue(encrypted);
        if (value.isEmpty() || value.length() > maxLength) {
            throw authenticationFailed();
        }
        return value;
    }

    private LocalDate birthdateClaim(String encrypted) {
        String value = claim(encrypted, 8);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception exception) {
            throw authenticationFailed();
        }
    }

    /** 다대구 didclient 와 동일: Base64 → RSA/ECB/PKCS1Padding → UTF-8. 실패 사유·값은 응답에 싣지 않는다. */
    private String decryptValue(String encrypted) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plain = cipher.doFinal(
                Base64.getDecoder().decode(encrypted.trim())
            );
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            throw authenticationFailed();
        }
    }

    private DadaeguLoginOutcome.RegistrationRequired requireRegistration(
        String did, String name, String phone, LocalDate birthDate, Instant now
    ) {
        if (name == null) {
            throw authenticationFailed(); // tb_users.name 이 필수 — 이름 없이는 가입시킬 수 없다
        }
        String rawToken = OpaqueTokens.newToken();
        Instant expiresAt = now.plus(REGISTRATION_TOKEN_TTL);
        registrationTokenRepository.save(new DadaeguRegistrationToken(
            UUID.randomUUID(), OpaqueTokens.sha256Hex(rawToken), did, name, phone, birthDate,
            expiresAt, null, now));
        return new DadaeguLoginOutcome.RegistrationRequired(rawToken, expiresAt, maskName(name));
    }

    /** 가입 화면 확인용. 첫 글자·끝 글자만 보인다: 홍길동 → 홍*동, 홍길 → 홍*, 홍 → *. */
    static String maskName(String name) {
        int[] codePoints = name.codePoints().toArray();
        if (codePoints.length <= 1) {
            return "*";
        }
        String first = new String(codePoints, 0, 1);
        if (codePoints.length == 2) {
            return first + "*";
        }
        return first + "*".repeat(codePoints.length - 2)
            + new String(codePoints, codePoints.length - 1, 1);
    }

    /** 재로그인 시 다대구 값으로 프로필 갱신. 안 온 클레임은 기존 값을 지우지 않는다. */
    private void refreshProfile(
        User user,
        String name,
        String phone,
        LocalDate birthDate,
        Instant now
    ) {
        String newName = name != null ? name : user.name();
        String newPhone = phone != null ? phone : user.phone();
        LocalDate newBirthDate = birthDate != null ? birthDate : user.birthDate();
        // 다대구 값이 저장된 프로필과 같으면 갱신 생략
        if (Objects.equals(newName, user.name())
            && Objects.equals(newPhone, user.phone())
            && Objects.equals(newBirthDate, user.birthDate())) {
            return;
        }
        userRepository.updateProfile(user.withProfile(
            newName, user.email(), newPhone, newBirthDate,
            user.career(), user.introduction(), user.profileImageId(), now
        ));
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

    private ApiException authenticationFailed() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTHENTICATION_FAILED,
            "Authentication failed"
        );
    }
}
