package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationRepository;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultInstitutionJoinApplicationService
    implements InstitutionJoinApplicationService {

    private final InstitutionJoinApplicationRepository applicationRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRoleRepository userRoleRepository;
    private final InstitutionIdGenerator institutionIdGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultInstitutionJoinApplicationService(
        InstitutionJoinApplicationRepository applicationRepository,
        InstitutionRepository institutionRepository,
        UserRoleRepository userRoleRepository,
        InstitutionIdGenerator institutionIdGenerator,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.institutionRepository = institutionRepository;
        this.userRoleRepository = userRoleRepository;
        this.institutionIdGenerator = institutionIdGenerator;
        this.authorizationPolicy = authorizationPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InstitutionJoinApplication apply(ApplyInstitutionJoinCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = command.requestedAt();
        InstitutionJoinApplication application = new InstitutionJoinApplication(
            UUID.randomUUID(),
            command.institutionName(),
            command.institutionCode(),
            command.representativeName(),
            command.contactEmail(),
            command.contactPhone(),
            command.address(),
            InstitutionJoinApplicationStatus.PENDING,
            null,
            command.actor() == null ? null : command.actor().userId(),
            null, null, null,
            now, now);
        applicationRepository.save(application);
        return application;
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionJoinApplication get(
        UUID applicationId, AuthenticatedUserContext actor
    ) {
        InstitutionJoinApplication application =
            applicationRepository.findById(applicationId)
                .orElseThrow(this::notFound);
        if (application.applicantUserId() != null
            && Objects.equals(actor.userId(), application.applicantUserId())) {
            return application;
        }
        authorizationPolicy.requirePlatformAdmin(actor);
        return application;
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionJoinApplicationPage list(
        AuthenticatedUserContext actor, InstitutionJoinApplicationStatus status,
        int page, int size
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        int offset = Math.multiplyExact(page, size);
        List<InstitutionJoinApplication> data =
            applicationRepository.findPage(status, size, offset);
        long totalElements = applicationRepository.count(status);
        int totalPages = totalElements == 0
            ? 0 : (int) ((totalElements + size - 1) / size);
        return new InstitutionJoinApplicationPage(
            data, page, size, totalElements, totalPages);
    }

    @Override
    @Transactional
    public InstitutionJoinApplication approve(
        UUID applicationId, AuthenticatedUserContext actor
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        InstitutionJoinApplication application =
            applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(this::notFound);
        requirePending(application);

        String institutionCode = resolveInstitutionCode(application);
        if (institutionRepository.findActiveByCode(institutionCode).isPresent()) {
            throw new ApiException(
                HttpStatus.CONFLICT, ApiErrorCode.INSTITUTION_CODE_CONFLICT,
                "Institution code already exists");
        }
        Instant now = clock.instant();
        Institution institution = new Institution(
            Objects.requireNonNull(institutionIdGenerator.generate(), "generated id"),
            institutionCode,
            application.institutionName(),
            null,
            application.representativeName(),
            application.address(),
            application.contactPhone(),
            application.contactEmail(),
            InstitutionStatus.ACTIVE,
            now, now, null);
        institutionRepository.save(institution);

        if (application.applicantUserId() != null) {
            userRoleRepository.saveIfAbsent(new UserRoleAssignment(
                UUID.randomUUID(), application.applicantUserId(),
                institution.id(), UserRole.INSTITUTION_ADMIN, now));
        }

        InstitutionJoinApplication approved = new InstitutionJoinApplication(
            application.id(), application.institutionName(),
            application.institutionCode(), application.representativeName(),
            application.contactEmail(), application.contactPhone(),
            application.address(),
            InstitutionJoinApplicationStatus.APPROVED,
            null, application.applicantUserId(), actor.userId(), now,
            institution.id(), application.createdAt(), now);
        if (!applicationRepository.updateDecision(
            approved, InstitutionJoinApplicationStatus.PENDING)) {
            throw statusConflict();
        }
        return approved;
    }

    @Override
    @Transactional
    public InstitutionJoinApplication reject(
        UUID applicationId, String rejectionReason, AuthenticatedUserContext actor
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        InstitutionJoinApplication application =
            applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(this::notFound);
        requirePending(application);
        Instant now = clock.instant();
        InstitutionJoinApplication rejected = new InstitutionJoinApplication(
            application.id(), application.institutionName(),
            application.institutionCode(), application.representativeName(),
            application.contactEmail(), application.contactPhone(),
            application.address(),
            InstitutionJoinApplicationStatus.REJECTED,
            rejectionReason, application.applicantUserId(), actor.userId(), now,
            null, application.createdAt(), now);
        if (!applicationRepository.updateDecision(
            rejected, InstitutionJoinApplicationStatus.PENDING)) {
            throw statusConflict();
        }
        return rejected;
    }

    private String resolveInstitutionCode(InstitutionJoinApplication application) {
        if (application.institutionCode() != null
            && !application.institutionCode().isBlank()) {
            return application.institutionCode().trim();
        }
        // 신청 시 코드가 없으면 신청 식별자 기반의 코드를 생성한다.
        return "INST-" + application.id().toString()
            .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private void requirePending(InstitutionJoinApplication application) {
        if (application.status() != InstitutionJoinApplicationStatus.PENDING) {
            throw statusConflict();
        }
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.INSTITUTION_APPLICATION_NOT_FOUND,
            "Institution application not found");
    }

    private ApiException statusConflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.INSTITUTION_APPLICATION_STATUS_CONFLICT,
            "Institution application is not pending");
    }
}
