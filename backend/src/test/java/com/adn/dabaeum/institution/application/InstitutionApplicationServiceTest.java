package com.adn.dabaeum.institution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InstitutionApplicationServiceTest {

    private static final UUID FIXED_ID =
        UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FIXED_INSTANT =
        Instant.parse("2026-08-03T00:00:00Z");

    @Mock
    InstitutionRepository repository;

    @Mock
    InstitutionIdGenerator idGenerator;

    private InstitutionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInstitutionApplicationService(
            repository,
            idGenerator,
            Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
        );
    }

    @Test
    void createsActiveInstitutionWithGeneratedIdAndClock() {
        when(idGenerator.generate()).thenReturn(FIXED_ID);
        when(repository.findActiveByCode("INST-001"))
            .thenReturn(Optional.empty());

        Institution created = service.create(commandWithoutStatus());

        assertThat(created.id()).isEqualTo(FIXED_ID);
        assertThat(created.status()).isEqualTo(InstitutionStatus.ACTIVE);
        assertThat(created.createdAt()).isEqualTo(FIXED_INSTANT);
        assertThat(created.updatedAt()).isEqualTo(FIXED_INSTANT);
        verify(repository).save(created);
    }

    @Test
    void preservesAllNullableCreateFields() {
        when(idGenerator.generate()).thenReturn(FIXED_ID);
        when(repository.findActiveByCode("INST-002"))
            .thenReturn(Optional.empty());
        CreateInstitutionCommand command = new CreateInstitutionCommand(
            " INST-002 ",
            " 기관명 ",
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            InstitutionStatus.SUSPENDED
        );

        Institution created = service.create(command);

        assertThat(created.institutionCode()).isEqualTo("INST-002");
        assertThat(created.name()).isEqualTo("기관명");
        assertThat(created.businessNumber()).isEqualTo("123-45-67890");
        assertThat(created.representativeName()).isEqualTo("홍길동");
        assertThat(created.address()).isEqualTo("서울시 중구");
        assertThat(created.contactPhone()).isEqualTo("02-1234-5678");
        assertThat(created.contactEmail()).isEqualTo("contact@example.com");
        assertThat(created.status()).isEqualTo(InstitutionStatus.SUSPENDED);
    }

    @Test
    void rejectsBlankInstitutionCodeOrNameAfterTrim() {
        ApiException exception = catchThrowableOfType(
            () -> service.create(new CreateInstitutionCommand(
                "  ",
                "기관명",
                null,
                null,
                null,
                null,
                null,
                null
            )),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsDuplicateInstitutionCodeBeforeSave() {
        Institution existing = new Institution(
            UUID.randomUUID(),
            "INST-DUP",
            "기존 기관",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
        when(repository.findActiveByCode("INST-DUP"))
            .thenReturn(Optional.of(existing));

        ApiException exception = catchThrowableOfType(
            () -> service.create(new CreateInstitutionCommand(
                "INST-DUP",
                "새 기관",
                null,
                null,
                null,
                null,
                null,
                null
            )),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(
            org.springframework.http.HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo(
            ApiErrorCode.INSTITUTION_CODE_CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    void mapsSaveIntegrityViolationToConflictWithoutRawDatabaseMessage() {
        when(idGenerator.generate()).thenReturn(FIXED_ID);
        when(repository.findActiveByCode("INST-RACE"))
            .thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("database detail"))
            .when(repository)
            .save(any());

        ApiException exception = catchThrowableOfType(
            () -> service.create(new CreateInstitutionCommand(
                "INST-RACE",
                "경합 기관",
                null,
                null,
                null,
                null,
                null,
                null
            )),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(
            org.springframework.http.HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo(
            ApiErrorCode.INSTITUTION_CODE_CONFLICT);
        assertThat(exception.getMessage()).doesNotContain("database detail");
    }

    @Test
    void returnsNotFoundWhenActiveInstitutionDoesNotExist() {
        when(repository.findActiveById(FIXED_ID)).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
            () -> service.get(FIXED_ID),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(
            org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(
            ApiErrorCode.INSTITUTION_NOT_FOUND);
    }

    @Test
    void returnsTheActiveInstitutionWhenPresent() {
        Institution institution = new Institution(
            FIXED_ID,
            "INST-GET",
            "조회 기관",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
        when(repository.findActiveById(FIXED_ID))
            .thenReturn(Optional.of(institution));

        assertThat(service.get(FIXED_ID)).isSameAs(institution);
    }

    @Test
    void listsInstitutionsUsingTypedCriteriaAndCalculatesTotalPages() {
        List<Institution> institutions = List.of(
            institution("INST-001", "첫 기관"),
            institution("INST-002", "둘 기관")
        );
        when(repository.findActivePage(any())).thenReturn(institutions);
        when(repository.countActive()).thenReturn(21L);

        InstitutionPage page = service.list(
            new ListInstitutionsQuery(2, 10, "name,asc")
        );

        ArgumentCaptor<InstitutionPageCriteria> criteria =
            ArgumentCaptor.forClass(InstitutionPageCriteria.class);
        verify(repository).findActivePage(criteria.capture());
        verify(repository).countActive();
        assertThat(criteria.getValue().offset()).isEqualTo(20);
        assertThat(criteria.getValue().limit()).isEqualTo(10);
        assertThat(criteria.getValue().sort()).isEqualTo(InstitutionSort.NAME);
        assertThat(criteria.getValue().direction()).isEqualTo(SortDirection.ASC);
        assertThat(page.data()).containsExactlyElementsOf(institutions);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(21L);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void calculatesZeroPagesForEmptyResult() {
        when(repository.findActivePage(any())).thenReturn(List.of());
        when(repository.countActive()).thenReturn(0L);

        InstitutionPage page = service.list(
            new ListInstitutionsQuery(0, 20, "createdAt,desc")
        );

        assertThat(page.totalPages()).isZero();
    }

    @Test
    void rejectsMalformedListQueryWithoutCallingRepository() {
        for (String sort : List.of(
            "name",
            "name,asc,extra",
            "unknown,asc",
            "name,up"
        )) {
            ApiException exception = catchThrowableOfType(
                () -> service.list(new ListInstitutionsQuery(0, 20, sort)),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(
                org.springframework.http.HttpStatus.BAD_REQUEST);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidListPageSizeAndOffsetOverflow() {
        for (ListInstitutionsQuery query : List.of(
            new ListInstitutionsQuery(-1, 20, "createdAt,desc"),
            new ListInstitutionsQuery(0, 0, "createdAt,desc"),
            new ListInstitutionsQuery(0, 101, "createdAt,desc"),
            new ListInstitutionsQuery(Integer.MAX_VALUE, 2, "createdAt,desc")
        )) {
            ApiException exception = catchThrowableOfType(
                () -> service.list(query),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(
                org.springframework.http.HttpStatus.BAD_REQUEST);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void preservesNullableUpdateAndOmittedFields() {
        Institution existing = institution(
            "INST-UPDATE",
            "기존 기관",
            "123",
            "대표",
            "기존 주소",
            "02-1",
            "old@example.com",
            InstitutionStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z")
        );
        when(repository.findActiveById(FIXED_ID))
            .thenReturn(Optional.of(existing));
        when(repository.updateActive(any())).thenReturn(true);

        Institution updated = service.update(new UpdateInstitutionCommand(
            FIXED_ID,
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.present(null),
            UpdateField.absent(),
            UpdateField.present("새 주소"),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent()
        ));

        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.institutionCode()).isEqualTo(existing.institutionCode());
        assertThat(updated.name()).isEqualTo(existing.name());
        assertThat(updated.businessNumber()).isNull();
        assertThat(updated.address()).isEqualTo("새 주소");
        assertThat(updated.createdAt()).isEqualTo(existing.createdAt());
        assertThat(updated.deletedAt()).isEqualTo(existing.deletedAt());
        assertThat(updated.updatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void rejectsEmptyUpdateAndNullOrBlankRequiredFields() {
        when(repository.findActiveById(FIXED_ID))
            .thenReturn(Optional.of(institution("INST-UPDATE", "기관")));
        for (UpdateInstitutionCommand command : List.of(
            emptyUpdate(),
            new UpdateInstitutionCommand(
                FIXED_ID,
                UpdateField.present(null),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent()
            ),
            new UpdateInstitutionCommand(
                FIXED_ID,
                UpdateField.present("  "),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent()
            ),
            new UpdateInstitutionCommand(
                FIXED_ID,
                UpdateField.absent(),
                UpdateField.present(null),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent()
            ),
            new UpdateInstitutionCommand(
                FIXED_ID,
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.absent(),
                UpdateField.present(null)
            )
        )) {
            ApiException exception = catchThrowableOfType(
                () -> service.update(command),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        }
    }

    @Test
    void skipsDuplicateLookupWhenInstitutionCodeIsUnchanged() {
        Institution existing = institution("INST-SAME", "기관");
        when(repository.findActiveById(FIXED_ID))
            .thenReturn(Optional.of(existing));
        when(repository.updateActive(any())).thenReturn(true);

        service.update(new UpdateInstitutionCommand(
            FIXED_ID,
            UpdateField.present(" INST-SAME "),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent()
        ));

        verify(repository, never()).findActiveByCode(any());
    }

    @Test
    void mapsChangedCodeConflictAndIntegrityViolation() {
        Institution existing = institution("INST-OLD", "기관");
        Institution other = institution(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "INST-NEW",
            "다른 기관"
        );
        when(repository.findActiveById(FIXED_ID))
            .thenReturn(Optional.of(existing));
        when(repository.findActiveByCode("INST-NEW"))
            .thenReturn(Optional.of(other));

        ApiException duplicate = catchThrowableOfType(
            () -> service.update(commandWithCode("INST-NEW")),
            ApiException.class
        );
        assertThat(duplicate.code()).isEqualTo(
            ApiErrorCode.INSTITUTION_CODE_CONFLICT);
        verify(repository, never()).updateActive(any());

        when(repository.findActiveByCode("INST-RACE"))
            .thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("database detail"))
            .when(repository).updateActive(any());

        ApiException integrity = catchThrowableOfType(
            () -> service.update(commandWithCode("INST-RACE")),
            ApiException.class
        );
        assertThat(integrity.status()).isEqualTo(
            org.springframework.http.HttpStatus.CONFLICT);
        assertThat(integrity.code()).isEqualTo(
            ApiErrorCode.INSTITUTION_CODE_CONFLICT);
        assertThat(integrity.getMessage()).doesNotContain("database detail");
    }

    @Test
    void mapsMissingOrConcurrentDeletedInstitutionToNotFound() {
        when(repository.findActiveById(FIXED_ID)).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(
            () -> service.update(commandWithCode("INST-NEW")),
            ApiException.class
        ).code()).isEqualTo(ApiErrorCode.INSTITUTION_NOT_FOUND);

        Institution existing = institution("INST-OLD", "기관");
        when(repository.findActiveById(FIXED_ID))
            .thenReturn(Optional.of(existing));
        when(repository.findActiveByCode("INST-NEW"))
            .thenReturn(Optional.empty());
        when(repository.updateActive(any())).thenReturn(false);

        assertThat(catchThrowableOfType(
            () -> service.update(commandWithCode("INST-NEW")),
            ApiException.class
        ).code()).isEqualTo(ApiErrorCode.INSTITUTION_NOT_FOUND);
    }

    private UpdateInstitutionCommand emptyUpdate() {
        return new UpdateInstitutionCommand(
            FIXED_ID,
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent()
        );
    }

    private UpdateInstitutionCommand commandWithCode(String code) {
        return new UpdateInstitutionCommand(
            FIXED_ID,
            UpdateField.present(code),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent()
        );
    }

    private Institution institution(String code, String name) {
        return institution(
            FIXED_ID,
            code,
            name,
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
    }

    private Institution institution(
        UUID id,
        String code,
        String name
    ) {
        return institution(
            id,
            code,
            name,
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null
        );
    }

    private Institution institution(
        String code,
        String name,
        String businessNumber,
        String representativeName,
        String address,
        String contactPhone,
        String contactEmail,
        InstitutionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        return institution(
            FIXED_ID,
            code,
            name,
            businessNumber,
            representativeName,
            address,
            contactPhone,
            contactEmail,
            status,
            createdAt,
            updatedAt,
            deletedAt
        );
    }

    private Institution institution(
        UUID id,
        String code,
        String name,
        String businessNumber,
        String representativeName,
        String address,
        String contactPhone,
        String contactEmail,
        InstitutionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        return new Institution(
            id,
            code,
            name,
            businessNumber,
            representativeName,
            address,
            contactPhone,
            contactEmail,
            status,
            createdAt,
            updatedAt,
            deletedAt
        );
    }

    private CreateInstitutionCommand commandWithoutStatus() {
        return new CreateInstitutionCommand(
            " INST-001 ",
            " 다배움 기관 ",
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
