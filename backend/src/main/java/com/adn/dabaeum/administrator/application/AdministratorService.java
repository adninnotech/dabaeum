package com.adn.dabaeum.administrator.application;

import com.adn.dabaeum.administrator.domain.Administrator;
import com.adn.dabaeum.administrator.domain.AdministratorCriteria;
import com.adn.dabaeum.administrator.domain.AdministratorPage;
import com.adn.dabaeum.administrator.domain.AdministratorRepository;
import com.adn.dabaeum.authentication.application.TemporaryPasswords;
import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.EmailAddress;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정 목록·생성 (설계서 §10.1). 역할 부여·회수·비활성화는 기존 /users/{id}/roles, /users/{id}/status 를 쓴다.
 * 생성 계정은 본인 가입이 아니라 약관 이력을 남기지 않는다.
 */
@Service
@Profile({"local", "dev"})
public class AdministratorService {

    private static final Set<UserRole> ADMIN_ROLES = EnumSet.of(UserRole.PLATFORM_ADMIN, UserRole.INSTITUTION_ADMIN);
    private static final int MAX_NAME_LENGTH = 100;
    static final int MAX_PAGE_SIZE = 100;

    private final AdministratorRepository administrators;
    private final UserRepository users;
    private final LocalAccountRepository accounts;
    private final UserRoleRepository roles;
    private final InstitutionRepository institutions;
    private final UserIdGenerator userIds;
    private final IdentityIdGenerator identityIds;
    private final RoleIdGenerator roleIds;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdministratorService(
        AdministratorRepository administrators,
        UserRepository users,
        LocalAccountRepository accounts,
        UserRoleRepository roles,
        InstitutionRepository institutions,
        UserIdGenerator userIds,
        IdentityIdGenerator identityIds,
        RoleIdGenerator roleIds,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.administrators = Objects.requireNonNull(administrators);
        this.users = Objects.requireNonNull(users);
        this.accounts = Objects.requireNonNull(accounts);
        this.roles = Objects.requireNonNull(roles);
        this.institutions = Objects.requireNonNull(institutions);
        this.userIds = Objects.requireNonNull(userIds);
        this.identityIds = Objects.requireNonNull(identityIds);
        this.roleIds = Objects.requireNonNull(roleIds);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public AdministratorPage list(int page, int size, UserRole role, UserStatus status, String q) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Invalid page request", List.of("page"));
        }
        if (role != null && !ADMIN_ROLES.contains(role)) {
            throw validation("role");
        }
        AdministratorPage found = administrators.search(new AdministratorCriteria(
            role, status, KeywordQuery.normalize(q), clock.instant(), Math.multiplyExact(page, size), size));
        return new AdministratorPage(withRoles(found.administrators()), found.totalElements());
    }

    @Transactional
    public CreatedAdministrator create(CreateAdministratorCommand command) {
        if (command == null) {
            throw validation("requestBody");
        }
        String name = requireName(command.name());
        String email = EmailAddress.deliverable(command.email()).orElseThrow(() -> validation("email"));
        UserRole role = command.role();
        if (role == null || !ADMIN_ROLES.contains(role)) {
            throw validation("role");
        }
        UUID institutionId = command.institutionId();
        if ((role == UserRole.INSTITUTION_ADMIN) == (institutionId == null)) {
            throw validation("institutionId");
        }
        if (institutionId != null && institutions.findActiveById(institutionId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.INSTITUTION_NOT_FOUND, "Institution not found");
        }
        if (accounts.findByNormalizedEmail(email).isPresent()) {
            throw identityConflict();
        }

        Instant now = clock.instant();
        UUID userId = Objects.requireNonNull(userIds.generate(), "generated user id");
        saveUser(User.registered(userId, name, email, null, null, now));
        String temporaryPassword = TemporaryPasswords.generate(secureRandom);
        accounts.save(new LocalAccountCredential(
            Objects.requireNonNull(identityIds.generate(), "generated identity id"),
            userId, email, passwordEncoder.encode(temporaryPassword), now, now));
        // 임시 비밀번호는 "바꾼 적 없음" — 첫 로그인부터 passwordExpired 로 변경을 안내한다.
        accounts.markPasswordTemporary(userId);
        roles.save(new UserRoleAssignment(
            Objects.requireNonNull(roleIds.generate(), "generated role id"), userId, institutionId, role, now));

        Administrator created = administrators.findById(userId, now)
            .orElseThrow(() -> new IllegalStateException("created administrator not found"));
        return new CreatedAdministrator(withRoles(List.of(created)).getFirst(), temporaryPassword);
    }

    private void saveUser(User user) {
        try {
            users.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw identityConflict();
        }
    }

    private List<Administrator> withRoles(List<Administrator> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<UUID, List<RoleSummary>> found = roles.findByUserIds(rows.stream().map(Administrator::userId).toList());
        return rows.stream().map(row -> row.withRoles(found.getOrDefault(row.userId(), List.of()))).toList();
    }

    private String requireName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw validation("name");
        }
        return trimmed;
    }

    private static ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }

    private static ApiException identityConflict() {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.IDENTITY_CONFLICT, "Identity already exists");
    }
}
