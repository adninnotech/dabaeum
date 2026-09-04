package com.adn.dabaeum.identity.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultIdentityApplicationService
    implements IdentityApplicationService {

    private final UserIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final IdentityIdGenerator idGenerator;
    private final Clock clock;

    public DefaultIdentityApplicationService(
        UserIdentityRepository identityRepository,
        UserRepository userRepository,
        IdentityIdGenerator idGenerator,
        Clock clock
    ) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserIdentity> list(UUID userId) {
        requireUser(userId);
        return List.copyOf(identityRepository.findByUserId(userId));
    }

    @Override
    @Transactional
    public UserIdentity link(LinkIdentityCommand command) {
        if (command == null || command.userId() == null) {
            throw validation("userId");
        }
        requireUser(command.userId());
        if (command.provider() == null) {
            throw validation("provider");
        }
        String providerSubject = normalizeRequired(
            command.providerSubject(),
            "providerSubject"
        );
        String externalDid = normalizeOptional(
            command.externalDid(),
            "externalDid"
        );
        if (!Boolean.TRUE.equals(command.verified())) {
            throw notVerified();
        }

        if (identityRepository.findByProviderSubject(
            command.provider(),
            providerSubject
        ).isPresent()) {
            throw conflict();
        }

        Instant now = clock.instant();
        UserIdentity identity = new UserIdentity(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            command.userId(),
            command.provider(),
            providerSubject,
            externalDid,
            now,
            null,
            now,
            now
        );
        try {
            identityRepository.save(identity);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return identity;
    }

    @Override
    @Transactional
    public UserIdentity unlink(UUID userId, UUID identityId) {
        requireUser(userId);
        UserIdentity identity = identityRepository.findById(identityId)
            .filter(candidate -> candidate.userId().equals(userId))
            .orElseThrow(this::identityNotFound);

        long verifiedCount = identityRepository.findByUserId(userId)
            .stream()
            .filter(candidate -> candidate.verifiedAt() != null)
            .count();
        if (identity.verifiedAt() != null && verifiedCount <= 1) {
            throw identityRequired();
        }

        if (!identityRepository.deleteByIdAndUserId(identityId, userId)) {
            throw identityNotFound();
        }
        return identity;
    }

    private void requireUser(UUID userId) {
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            throw userNotFound();
        }
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw validation(field);
        }
        return value.trim();
    }

    private String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        if (value.trim().isEmpty()) {
            throw validation(field);
        }
        return value.trim();
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field)
        );
    }

    private ApiException userNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND,
            "User not found"
        );
    }

    private ApiException identityNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.IDENTITY_NOT_FOUND,
            "Identity not found"
        );
    }

    private ApiException conflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.IDENTITY_CONFLICT,
            "Identity already exists"
        );
    }

    private ApiException identityRequired() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.IDENTITY_REQUIRED,
            "At least one verified identity is required"
        );
    }

    private ApiException notVerified() {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.IDENTITY_NOT_VERIFIED,
            "Identity verification is required"
        );
    }
}
