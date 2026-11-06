package com.adn.dabaeum.user.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserListFilter;
import com.adn.dabaeum.user.domain.UserListItem;
import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserSort;
import com.adn.dabaeum.user.domain.UserSortDirection;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultUserApplicationService implements UserApplicationService {

    public static final String REQUEST_BODY = "requestBody";

    /** 생년월일의 "오늘"은 서울 날짜로 본다(회원가입 @PastOrPresent 와 같은 기준). */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserRepository repository;
    private final StoredFileRepository storedFileRepository;
    private final UserIdGenerator idGenerator;
    private final UserRoleRepository userRoleRepository;
    private final UserInterestService interests;

    private final Clock clock;



    public DefaultUserApplicationService(
            UserRepository repository,
            UserIdGenerator idGenerator,
            Clock clock,
            StoredFileRepository storedFileRepository,
            UserRoleRepository userRoleRepository,
            UserInterestService interests
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.storedFileRepository =
                Objects.requireNonNull(storedFileRepository, "storedFileRepository");
        this.userRoleRepository =
                Objects.requireNonNull(userRoleRepository, "userRoleRepository");
        this.interests = Objects.requireNonNull(interests, "interests");
    }

    @Override
    @Transactional
    public User create(CreateUserCommand command) {
        if (command == null) {
            throw validation(REQUEST_BODY);
        }
        String name = normalizeRequired(command.name(), "name");
        LocalDate birthDate = requireNotFuture(command.birthDate());
        UUID id = Objects.requireNonNull(idGenerator.generate(), "generated id");
        Instant now = clock.instant();
        User user = new User(
                id,
                name,
                command.email(),
                command.phone(),
                birthDate,
                command.status() == null ? UserStatus.ACTIVE : command.status(),
                null,
                now,
                now
        );
        repository.save(user);
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPage list(ListUsersQuery query) {
        if (query == null) {
            throw badRequest("query");
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }

        String[] sortParts = splitSort(query.sort());
        UserSort sort = parseSort(sortParts[0]);
        UserSortDirection direction = parseDirection(sortParts[1]);
        int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }

        UserListFilter filter = UserListFilters.from(query, clock.instant());
        UserPageCriteria criteria = new UserPageCriteria(
                offset,
                query.size(),
                sort,
                direction,
                filter
        );
        List<UserListItem> data = repository.findPage(criteria);
        long totalElements = repository.count(filter);
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact(((totalElements - 1L) / query.size()) + 1L);
        return new UserPage(
                withRoles(data),
                query.page(),
                query.size(),
                totalElements,
                totalPages
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserView get(UUID userId) {
        if (userId == null) {
            throw badRequest("userId");
        }
        return view(repository.findById(userId).orElseThrow(this::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleSummary> rolesOf(UUID userId) {
        return userRoleRepository.findByUserIds(List.of(userId))
                .getOrDefault(userId, List.of());
    }

    /**
     * 역할은 사용자당 여러 개라 사용자 목록에 조인하면 행이 부풀어 페이지네이션이 깨진다.
     * 그래서 페이지를 먼저 읽고, 그 id 들로 역할을 한 번에 가져와 Java 에서 붙인다.
     * 페이지 크기 상한이 100 이라 IN 절 파라미터 수도 100 을 넘지 않는다.
     */
    private List<UserView> withRoles(List<UserListItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RoleSummary>> roles = userRoleRepository.findByUserIds(
                items.stream().map(item -> item.user().id()).toList()
        );
        return items.stream()
                .map(item -> new UserView(
                        item.user(),
                        roles.getOrDefault(item.user().id(), List.of()),
                        item.enrollmentCount(),
                        item.courseCount()
                ))
                .toList();
    }

    private UserView view(User user) {
        return new UserView(user, rolesOf(user.id()));
    }

    @Override
    @Transactional
    public User updateProfile(UpdateUserCommand command) {
        if (command == null || command.userId() == null) {
            throw validation(REQUEST_BODY);
        }
        return applyProfileUpdate(command);
    }

    /** updateProfile·updateMe 의 공통 본문. 트랜잭션은 부른 공개 메서드가 연다. */
    private User applyProfileUpdate(UpdateUserCommand command) {
        if (command.presentFieldCount() == 0) {
            throw validation(REQUEST_BODY);
        }

        // 값 검증은 사용자 조회보다 먼저 한다. 잘못된 값이면 존재 여부와 무관하게 422.
        UserUpdateField<String> name = command.name().map(v -> normalizeRequired(v, "name"));
        UserUpdateField<String> career =
                command.career().map(v -> normalizeNullable(v, "career", 2000));
        UserUpdateField<String> introduction =
                command.introduction().map(v -> normalizeNullable(v, "introduction", 2000));
        UserUpdateField<LocalDate> birthDate = command.birthDate().map(this::requireNotFuture);

        User existing = repository.findById(command.userId())
                .orElseThrow(this::notFound);
        if (command.profileImageId().present() && command.profileImageId().value() != null) {
            requireProfileImage(command.profileImageId().value(), existing.id());
        }

        User updated = existing.withProfile(
                name.orElse(existing.name()),
                command.email().orElse(existing.email()),
                command.phone().orElse(existing.phone()),
                birthDate.orElse(existing.birthDate()),
                career.orElse(existing.career()),
                introduction.orElse(existing.introduction()),
                command.profileImageId().orElse(existing.profileImageId()),
                clock.instant()
        ).withGender(command.gender().orElse(existing.gender()));
        if (!repository.updateProfile(updated)) {
            throw notFound();
        }
        return updated;
    }

    @Override
    @Transactional
    public User changeStatus(ChangeUserStatusCommand command) {
        if (command == null || command.userId() == null) {
            throw validation(REQUEST_BODY);
        }
        if (command.status() == null) {
            throw validation("status");
        }

        User existing = repository.findById(command.userId())
                .orElseThrow(this::notFound);
        if (!existing.status().canTransitionTo(command.status())) {
            throw statusConflict();
        }

        Instant now = clock.instant();
        Instant withdrawnAt = command.status() == UserStatus.WITHDRAWN ? now : null;
        if (!repository.updateStatus(command.userId(), command.status(), withdrawnAt, now)) {
            throw notFound();
        }
        // 정지·휴면·탈퇴로 바뀌면 이미 발급된 토큰도 즉시 막는다. 복귀(ACTIVE)는 새 로그인으로 받는다.
        if (command.status() != UserStatus.ACTIVE) {
            repository.invalidateTokens(command.userId(), now);
        }

        return existing.withStatus(command.status(), withdrawnAt, now);
    }

    @Override
    @Transactional
    public User withdrawSelf(UUID currentUserId) {
        requireCurrentUserId(currentUserId);

        User existing = repository.findById(currentUserId).orElseThrow(this::notFound);
        if (!existing.status().canTransitionTo(UserStatus.WITHDRAWN)) {
            throw statusConflict();
        }

        Instant now = clock.instant();
        if (!repository.updateStatus(currentUserId, UserStatus.WITHDRAWN, now, now)) {
            throw notFound();
        }
        // 탈퇴한 사용자가 기존 토큰으로 계속 API 를 쓰지 못하도록 즉시 무효화한다.
        repository.invalidateTokens(currentUserId, now);

        // 탈퇴는 상태만 바꾼다: 수강·수강평 등 이력은 기관의 통계·수료 증빙으로 남아야 하므로
        // 여기서 다른 리포지토리를 건드리거나 데이터를 지우지 않는다. 지우는 코드를 추가하지 말 것.
        return existing.withStatus(UserStatus.WITHDRAWN, now, now);
    }

    @Override
    @Transactional
    public User updateNotificationPreferences(
        UUID currentUserId,
        Boolean emailNotificationEnabled,
        Boolean smsNotificationEnabled
    ) {
        requireCurrentUserId(currentUserId);
        if (emailNotificationEnabled == null && smsNotificationEnabled == null) {
            throw validation(REQUEST_BODY);
        }

        User existing = repository.findById(currentUserId).orElseThrow(this::notFound);
        boolean email = emailNotificationEnabled == null
                ? existing.emailNotificationEnabled()
                : emailNotificationEnabled;
        boolean sms = smsNotificationEnabled == null
                ? existing.smsNotificationEnabled()
                : smsNotificationEnabled;

        Instant now = clock.instant();
        if (!repository.updateNotificationPreferences(currentUserId, email, sms, now)) {
            throw notFound();
        }
        return existing.withNotificationPreferences(email, sms, now);
    }

    @Override
    @Transactional
    public User updateAdminMemo(UUID userId, String memo) {
        User existing = repository.findById(userId).orElseThrow(this::notFound);
        Instant now = clock.instant();
        if (!repository.updateAdminMemo(userId, memo, now)) {
            throw notFound();
        }
        return existing.withAdminMemo(memo, now);
    }

    /** 프로필 이미지는 본인이 PROFILE 용도로 올린 파일만 연결할 수 있다. */
    private void requireProfileImage(UUID fileId, UUID userId) {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> validation("profileImageId"));
        if (file.purpose() != StoredFilePurpose.PROFILE
                || !Objects.equals(file.uploadedBy(), userId)) {
            throw validation("profileImageId");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getMe(UUID currentUserId) {
        requireCurrentUserId(currentUserId);
        return view(repository.findById(currentUserId).orElseThrow(this::notFound));
    }

    @Override
    @Transactional
    public User updateMe(UUID currentUserId, UpdateMeCommand command) {
        requireCurrentUserId(currentUserId);
        if (command == null) {
            throw validation(REQUEST_BODY);
        }

        return applyProfileUpdate(toUpdateUserCommand(currentUserId, command));
    }

    @Override
    @Transactional
    public User updateMeWithInterests(
        UUID currentUserId, UpdateMeCommand command, List<String> codes, Boolean agreed
    ) {
        requireCurrentUserId(currentUserId);
        if (command == null || codes == null) {
            throw validation(REQUEST_BODY);
        }
        if (!codes.isEmpty() && !isLearner(currentUserId)) {
            throw validation("interestCodes");
        }
        interests.updateMine(currentUserId, codes, agreed, clock.instant());
        UpdateUserCommand profile = toUpdateUserCommand(currentUserId, command);
        if (profile.presentFieldCount() > 0) {
            return applyProfileUpdate(profile);
        }
        return repository.findById(currentUserId).orElseThrow(this::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public UserInterests interestsOf(UUID userId) {
        return interests.view(userId);
    }

    private boolean isLearner(UUID userId) {
        return rolesOf(userId).stream().anyMatch(role -> role.role() == UserRole.LEARNER);
    }

    private static UpdateUserCommand toUpdateUserCommand(UUID userId, UpdateMeCommand command) {
        return new UpdateUserCommand(
            userId,
            command.name(),
            command.email(),
            command.phone(),
            command.birthDate(),
            command.career(),
            command.introduction(),
            command.profileImageId(),
            command.gender()
        );
    }

    private String[] splitSort(String sort) {
        if (sort == null) {
            throw badRequest("sort");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2) {
            throw badRequest("sort");
        }
        return parts;
    }

    private UserSort parseSort(String value) {
        try {
            return UserSort.fromApiValue(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
    }

    private UserSortDirection parseDirection(String value) {
        try {
            return UserSortDirection.fromApiValue(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
    }

    private String normalizeRequired(String value, String field) {
        if (value == null) {
            throw validation(field);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw validation(field);
        }
        return normalized;
    }

    /** null은 "비움"으로 허용하고, 값이 있으면 공백 제거 후 길이만 검사한다. */
    private String normalizeNullable(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw validation(field);
        }
        return normalized;
    }

    /** null(지우기)은 그대로 받는다. 서울 기준 오늘보다 뒤면 422. */
    private LocalDate requireNotFuture(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.ofInstant(clock.instant(), SEOUL))) {
            throw validation("birthDate");
        }
        return birthDate;
    }

    private ApiException validation(String field) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                List.of(field)
        );
    }

    private void requireCurrentUserId(UUID currentUserId) {
        if (currentUserId == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.UNAUTHORIZED,
                    "Authentication required"
            );
        }
    }

    private ApiException badRequest(String field) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid",
                List.of(field)
        );
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.USER_NOT_FOUND,
                "User not found"
        );
    }

    private ApiException statusConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.USER_STATUS_CONFLICT,
                "User status cannot be changed"
        );
    }
}
