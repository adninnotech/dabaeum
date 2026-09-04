package com.adn.dabaeum.authentication.application;

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
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
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
 * 복호화해 처음 보는 DID면 사용자를 만들고, 재로그인이면 프로필을 다대구 값으로 갱신한다.
 * ci·성별·내외국인은 받지도 저장하지도 않는다.
 */
@Service
@Profile({"local", "dev"})
@EnableConfigurationProperties(DadaeguLoginProperties.class)
public class DadaeguLoginService {

    private static final IdentityProvider PROVIDER = IdentityProvider.DADAEGU;
    private static final String CIPHER = "RSA/ECB/PKCS1Padding";

    private final PrivateKey privateKey;
    private final UserIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserIdGenerator userIdGenerator;
    private final IdentityIdGenerator identityIdGenerator;
    private final RoleIdGenerator roleIdGenerator;
    private final LocalAccessTokenService tokenService;
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
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public LocalAuthResult login(
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
        UserIdentity identity = identityRepository
            .findByProviderSubject(PROVIDER, did)
            .orElseGet(() -> register(did, name, phone, birthDate, now));
        User user = userRepository.findById(identity.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            throw authenticationFailed();
        }
        refreshProfile(user, name, phone, birthDate, now);
        return LocalAuthResult.of(
            tokenService.issue(user.id(), PROVIDER.name()),
            user.id(),
            PROVIDER.name(),
            userRoleRepository.findByUserId(user.id()),
            clock
        );
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

    private UserIdentity register(
        String did,
        String name,
        String phone,
        LocalDate birthDate,
        Instant now
    ) {
        UUID userId = Objects.requireNonNull(userIdGenerator.generate(), "generated user id");
        UserIdentity identity = new UserIdentity(
            Objects.requireNonNull(identityIdGenerator.generate(), "generated identity id"),
            userId,
            PROVIDER,
            did,
            did,
            now,
            null,
            now,
            now
        );
        try {
            userRepository.save(new User(
                userId, name, null, phone, birthDate, UserStatus.ACTIVE, null, now, now
            ));
            identityRepository.save(identity);
        } catch (DataIntegrityViolationException exception) {
            // ponytail: 같은 DID 동시 첫 로그인 — 트랜잭션이 이미 깨졌으니 409 로 돌려보내 재시도시킨다
            throw identityConflict();
        }
        userRoleRepository.save(new UserRoleAssignment(
            Objects.requireNonNull(roleIdGenerator.generate(), "generated role id"),
            userId,
            null,
            UserRole.LEARNER,
            now
        ));
        return identity;
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
        userRepository.updateProfile(new User(
            user.id(),
            newName,
            user.email(),
            newPhone,
            newBirthDate,
            user.status(),
            user.withdrawnAt(),
            user.createdAt(),
            now,
            user.career(),
            user.introduction(),
            user.profileImageId()
        ));
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
