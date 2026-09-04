package com.adn.dabaeum.user.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserSort;
import com.adn.dabaeum.user.domain.UserSortDirection;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultUserApplicationService implements UserApplicationService {

    private final UserRepository repository;
    private final UserIdGenerator idGenerator;
    private final Clock clock;
    private final StoredFileRepository storedFileRepository;

    public DefaultUserApplicationService(
        UserRepository repository,
        UserIdGenerator idGenerator,
        Clock clock,
        StoredFileRepository storedFileRepository
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.storedFileRepository =
            Objects.requireNonNull(storedFileRepository, "storedFileRepository");
    }

    @Override
    @Transactional
    public User create(CreateUserCommand command) {
        if (command == null) {
            throw validation("requestBody");
        }
        String name = normalizeRequired(command.name(), "name");
        UUID id = Objects.requireNonNull(idGenerator.generate(), "generated id");
        Instant now = clock.instant();
        User user = new User(
            id,
            name,
            command.email(),
            command.phone(),
            command.birthDate(),
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

        UserPageCriteria criteria = new UserPageCriteria(
            offset,
            query.size(),
            sort,
            direction,
            query.status()
        );
        List<User> data = repository.findPage(criteria);
        long totalElements = repository.count(query.status());
        int totalPages = totalElements == 0
            ? 0
            : Math.toIntExact(((totalElements - 1L) / query.size()) + 1L);
        return new UserPage(
            List.copyOf(data),
            query.page(),
            query.size(),
            totalElements,
            totalPages
        );
    }

    @Override
    @Transactional(readOnly = true)
    public User get(UUID userId) {
        if (userId == null) {
            throw badRequest("userId");
        }
        return repository.findById(userId).orElseThrow(this::notFound);
    }

    @Override
    @Transactional
    public User updateProfile(UpdateUserCommand command) {
        if (command == null || command.userId() == null) {
            throw validation("requestBody");
        }
        if (command.presentFieldCount() == 0) {
            throw validation("requestBody");
        }

        UserUpdateField<String> name = fieldOrAbsent(command.name());
        UserUpdateField<String> email = fieldOrAbsent(command.email());
        UserUpdateField<String> phone = fieldOrAbsent(command.phone());
        UserUpdateField<java.time.LocalDate> birthDate = fieldOrAbsent(
            command.birthDate()
        );
        UserUpdateField<String> career = fieldOrAbsent(command.career());
        UserUpdateField<String> introduction = fieldOrAbsent(command.introduction());
        UserUpdateField<UUID> profileImageId = fieldOrAbsent(command.profileImageId());
        String normalizedName = name.present()
            ? normalizeRequired(name.value(), "name")
            : null;
        String normalizedCareer = career.present()
            ? normalizeNullable(career.value(), "career", 2000) : null;
        String normalizedIntroduction = introduction.present()
            ? normalizeNullable(introduction.value(), "introduction", 2000) : null;

        User existing = repository.findById(command.userId())
            .orElseThrow(this::notFound);
        if (!name.present()) {
            normalizedName = existing.name();
        }
        if (profileImageId.present() && profileImageId.value() != null) {
            requireProfileImage(profileImageId.value(), existing.id());
        }

        User updated = new User(
            existing.id(),
            normalizedName,
            valueOrExisting(email, existing.email()),
            valueOrExisting(phone, existing.phone()),
            valueOrExisting(birthDate, existing.birthDate()),
            existing.status(),
            existing.withdrawnAt(),
            existing.createdAt(),
            clock.instant(),
            career.present() ? normalizedCareer : existing.career(),
            introduction.present() ? normalizedIntroduction : existing.introduction(),
            valueOrExisting(profileImageId, existing.profileImageId())
        );
        if (!repository.updateProfile(updated)) {
            throw notFound();
        }
        return updated;
    }

    @Override
    @Transactional
    public User changeStatus(ChangeUserStatusCommand command) {
        if (command == null || command.userId() == null) {
            throw validation("requestBody");
        }
        if (command.status() == null) {
            throw validation("status");
        }

        User existing = repository.findById(command.userId())
            .orElseThrow(this::notFound);
        if (!isAllowedTransition(existing.status(), command.status())) {
            throw statusConflict();
        }

        Instant now = clock.instant();
        Instant withdrawnAt = command.status() == UserStatus.WITHDRAWN
            ? now
            : null;
        if (!repository.updateStatus(
            command.userId(),
            command.status(),
            withdrawnAt,
            now
        )) {
            throw notFound();
        }
        // 정지·휴면·탈퇴로 바뀌면 이미 발급된 토큰도 즉시 막는다. 복귀(ACTIVE)는 새 로그인으로 받는다.
        if (command.status() != UserStatus.ACTIVE) {
            repository.invalidateTokens(command.userId(), now);
        }

        return new User(
            existing.id(),
            existing.name(),
            existing.email(),
            existing.phone(),
            existing.birthDate(),
            command.status(),
            withdrawnAt,
            existing.createdAt(),
            now,
            existing.career(),
            existing.introduction(),
            existing.profileImageId()
        );
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
    public User getMe(UUID currentUserId) {
        requireCurrentUserId(currentUserId);
        return repository.findById(currentUserId).orElseThrow(this::notFound);
    }

    @Override
    @Transactional
    public User updateMe(UUID currentUserId, UpdateMeCommand command) {
        requireCurrentUserId(currentUserId);
        if (command == null) {
            throw validation("requestBody");
        }
        return updateProfile(new UpdateUserCommand(
            currentUserId,
            command.name(),
            command.email(),
            command.phone(),
            command.birthDate(),
            command.career(),
            command.introduction(),
            command.profileImageId()
        ));
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

    private boolean isAllowedTransition(
        UserStatus current,
        UserStatus target
    ) {
        if (current == null || target == null || current == target) {
            return false;
        }
        return switch (current) {
            case ACTIVE -> target == UserStatus.DORMANT
                || target == UserStatus.SUSPENDED
                || target == UserStatus.WITHDRAWN;
            case DORMANT -> target == UserStatus.ACTIVE
                || target == UserStatus.SUSPENDED
                || target == UserStatus.WITHDRAWN;
            case SUSPENDED -> target == UserStatus.ACTIVE
                || target == UserStatus.DORMANT
                || target == UserStatus.WITHDRAWN;
            case WITHDRAWN -> false;
        };
    }

    private <T> UserUpdateField<T> fieldOrAbsent(UserUpdateField<T> field) {
        return field == null ? UserUpdateField.absent() : field;
    }

    private <T> T valueOrExisting(UserUpdateField<T> field, T existing) {
        return field.present() ? field.value() : existing;
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
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
