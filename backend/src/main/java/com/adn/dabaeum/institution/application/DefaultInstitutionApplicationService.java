package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionSort;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.institution.domain.SortDirection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultInstitutionApplicationService
    implements InstitutionApplicationService {

    private final InstitutionRepository repository;
    private final InstitutionIdGenerator idGenerator;
    private final Clock clock;

    public DefaultInstitutionApplicationService(
        InstitutionRepository repository,
        InstitutionIdGenerator idGenerator,
        Clock clock
    ) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Institution create(CreateInstitutionCommand command) {
        if (command == null) {
            throw validation("requestBody");
        }

        String institutionCode = normalizeRequired(
            command.institutionCode(),
            "institutionCode"
        );
        String name = normalizeRequired(command.name(), "name");

        if (repository.findActiveByCode(institutionCode).isPresent()) {
            throw conflict();
        }

        Instant now = clock.instant();
        Institution institution = new Institution(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            institutionCode,
            name,
            command.businessNumber(),
            command.representativeName(),
            command.address(),
            command.contactPhone(),
            command.contactEmail(),
            command.status() == null
                ? InstitutionStatus.ACTIVE
                : command.status(),
            now,
            now,
            null
        );

        try {
            repository.save(institution);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }

        return institution;
    }

    @Override
    @Transactional(readOnly = true)
    public Institution get(UUID institutionId) {
        return repository.findActiveById(institutionId)
            .orElseThrow(this::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionPage list(ListInstitutionsQuery query) {
        if (query == null) {
            throw badRequest("query");
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }

        String[] sortParts = splitSort(query.sort());
        InstitutionSort sort = parseSort(sortParts[0]);
        SortDirection direction = parseDirection(sortParts[1]);
        int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }

        InstitutionPageCriteria criteria = new InstitutionPageCriteria(
            offset,
            query.size(),
            sort,
            direction
        );
        List<Institution> data = repository.findActivePage(criteria);
        long totalElements = repository.countActive();
        int totalPages = totalElements == 0
            ? 0
            : Math.toIntExact(((totalElements - 1L) / query.size()) + 1L);

        return new InstitutionPage(
            List.copyOf(data),
            query.page(),
            query.size(),
            totalElements,
            totalPages
        );
    }

    @Override
    @Transactional
    public Institution update(UpdateInstitutionCommand command) {
        if (command == null || command.institutionId() == null) {
            throw validation("requestBody");
        }
        if (command.presentFieldCount() == 0) {
            throw validation("requestBody");
        }

        Institution existing = repository.findActiveById(command.institutionId())
            .orElseThrow(this::notFound);

        UpdateField<String> institutionCode = fieldOrAbsent(
            command.institutionCode());
        UpdateField<String> name = fieldOrAbsent(command.name());
        UpdateField<String> businessNumber = fieldOrAbsent(
            command.businessNumber());
        UpdateField<String> representativeName = fieldOrAbsent(
            command.representativeName());
        UpdateField<String> address = fieldOrAbsent(command.address());
        UpdateField<String> contactPhone = fieldOrAbsent(command.contactPhone());
        UpdateField<String> contactEmail = fieldOrAbsent(command.contactEmail());
        UpdateField<InstitutionStatus> status = fieldOrAbsent(command.status());

        String normalizedCode = existing.institutionCode();
        if (institutionCode.present()) {
            normalizedCode = normalizeRequired(
                institutionCode.value(),
                "institutionCode"
            );
            if (!normalizedCode.equals(existing.institutionCode())) {
                Optional<Institution> duplicate = repository.findActiveByCode(
                    normalizedCode
                );
                if (duplicate.isPresent()
                    && !duplicate.get().id().equals(existing.id())) {
                    throw conflict();
                }
            }
        }

        String normalizedName = existing.name();
        if (name.present()) {
            normalizedName = normalizeRequired(name.value(), "name");
        }

        if (status.present() && status.value() == null) {
            throw validation("status");
        }

        Instant now = clock.instant();
        Institution updated = new Institution(
            existing.id(),
            normalizedCode,
            normalizedName,
            valueOrExisting(businessNumber, existing.businessNumber()),
            valueOrExisting(representativeName, existing.representativeName()),
            valueOrExisting(address, existing.address()),
            valueOrExisting(contactPhone, existing.contactPhone()),
            valueOrExisting(contactEmail, existing.contactEmail()),
            valueOrExisting(status, existing.status()),
            existing.createdAt(),
            now,
            existing.deletedAt()
        );

        try {
            if (!repository.updateActive(updated)) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }

        return updated;
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

    private InstitutionSort parseSort(String value) {
        try {
            return InstitutionSort.fromApiValue(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
    }

    private SortDirection parseDirection(String value) {
        try {
            return SortDirection.fromApiValue(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
    }

    private <T> UpdateField<T> fieldOrAbsent(UpdateField<T> field) {
        return field == null ? UpdateField.absent() : field;
    }

    private <T> T valueOrExisting(UpdateField<T> field, T existing) {
        return field.present() ? field.value() : existing;
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

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field)
        );
    }

    private ApiException badRequest(String field) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid",
            List.of(field)
        );
    }

    private ApiException conflict() {
        return new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.INSTITUTION_CODE_CONFLICT,
            "Institution code already exists"
        );
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found"
        );
    }
}
