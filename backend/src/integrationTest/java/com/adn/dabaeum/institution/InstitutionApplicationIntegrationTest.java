package com.adn.dabaeum.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.institution.application.CreateInstitutionCommand;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.application.ListInstitutionsQuery;
import com.adn.dabaeum.institution.application.UpdateField;
import com.adn.dabaeum.institution.application.UpdateInstitutionCommand;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InstitutionApplicationIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    InstitutionApplicationService service;

    @Autowired
    InstitutionRepository repository;

    @Test
    void createsFullInstitutionAndRoundTripsDetail() {
        String code = uniqueCode("APP-FULL");
        Institution created = service.create(new CreateInstitutionCommand(
            code,
            "인수 전체 필드 기관",
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "acceptance@example.com",
            InstitutionStatus.ACTIVE
        ));

        assertThat(repository.findActiveById(created.id()))
            .contains(created);
        assertThat(service.get(created.id()))
            .isEqualTo(created);
    }

    @Test
    void listsAllStatusesAndExcludesSoftDeletedInstitution() {
        Institution active = create("APP-ACTIVE", InstitutionStatus.ACTIVE);
        Institution inactive = create("APP-INACTIVE", InstitutionStatus.INACTIVE);
        Institution suspended = create("APP-SUSPENDED", InstitutionStatus.SUSPENDED);
        Institution deleted = create("APP-DELETED", InstitutionStatus.ACTIVE);
        softDelete(deleted.id());

        InstitutionPage page = service.list(new ListInstitutionsQuery(
            0,
            100,
            "institutionCode,asc"
        ));

        assertThat(page.data())
            .extracting(Institution::id)
            .contains(active.id(), inactive.id(), suspended.id())
            .doesNotContain(deleted.id());
        assertThatThrownBy(() -> service.get(deleted.id()))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.INSTITUTION_NOT_FOUND);
    }

    @Test
    void updatesNullableFieldsAndUpdatedAtThroughApplication() {
        Institution original = service.create(new CreateInstitutionCommand(
            uniqueCode("APP-UPDATE"),
            "인수 수정 전",
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "before@example.com",
            InstitutionStatus.ACTIVE
        ));

        Institution updated = service.update(new UpdateInstitutionCommand(
            original.id(),
            UpdateField.absent(),
            UpdateField.present("인수 수정 후"),
            UpdateField.present(null),
            UpdateField.absent(),
            UpdateField.present(null),
            UpdateField.absent(),
            UpdateField.present(null),
            UpdateField.present(InstitutionStatus.SUSPENDED)
        ));

        assertThat(updated.name()).isEqualTo("인수 수정 후");
        assertThat(updated.businessNumber()).isNull();
        assertThat(updated.address()).isNull();
        assertThat(updated.contactEmail()).isNull();
        assertThat(updated.status()).isEqualTo(InstitutionStatus.SUSPENDED);
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(original.updatedAt());
        assertThat(repository.findActiveById(original.id())).contains(updated);
    }

    @Test
    void duplicateCodeCreateAndUpdateReturnConflictCode() {
        Institution existing = create("DUP-A", InstitutionStatus.ACTIVE);

        assertThatThrownBy(() -> service.create(new CreateInstitutionCommand(
            existing.institutionCode(),
            "중복 등록",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE
        )))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> {
                ApiException exception = (ApiException) error;
                assertThat(exception.status().value()).isEqualTo(409);
                assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.INSTITUTION_CODE_CONFLICT);
            });

        Institution target = create("DUP-B", InstitutionStatus.ACTIVE);
        assertThatThrownBy(() -> service.update(new UpdateInstitutionCommand(
            target.id(),
            UpdateField.present(existing.institutionCode()),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent(),
            UpdateField.absent()
        )))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.INSTITUTION_CODE_CONFLICT));
    }

    @Test
    void pageAndSortTotalsRemainConsistent() {
        long baseline = repository.countActive();
        create("APP-PAGE-1", InstitutionStatus.ACTIVE);
        create("APP-PAGE-2", InstitutionStatus.INACTIVE);
        create("APP-PAGE-3", InstitutionStatus.SUSPENDED);

        InstitutionPage first = service.list(new ListInstitutionsQuery(
            0,
            2,
            "institutionCode,asc"
        ));
        InstitutionPage second = service.list(new ListInstitutionsQuery(
            1,
            2,
            "institutionCode,asc"
        ));

        assertThat(first.size()).isEqualTo(2);
        assertThat(second.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(second.totalElements());
        assertThat(first.totalPages()).isEqualTo(
            (int) ((first.totalElements() + first.size() - 1) / first.size())
        );
        assertThat(first.totalElements()).isEqualTo(baseline + 3);
        assertThat(first.data()).hasSize(2);
        assertThat(second.data()).hasSize(Math.toIntExact(
            Math.min(2L, Math.max(0L, first.totalElements() - 2L))
        ));
        assertThat(first.data()).doesNotContainAnyElementsOf(second.data());
        assertThat(first.data()).isSortedAccordingTo(
            (left, right) -> left.institutionCode()
                .compareTo(right.institutionCode())
        );
        assertThat(second.data()).isSortedAccordingTo(
            (left, right) -> left.institutionCode()
                .compareTo(right.institutionCode())
        );
        assertThat(first.data().getLast().institutionCode())
            .isLessThanOrEqualTo(second.data().getFirst().institutionCode());
    }

    private Institution create(String prefix, InstitutionStatus status) {
        return service.create(new CreateInstitutionCommand(
            uniqueCode(prefix),
            "인수 기관 " + prefix,
            null,
            "대표자",
            "주소",
            "02-0000-0000",
            "acceptance@example.com",
            status
        ));
    }

    private void softDelete(UUID id) {
        int updated = jdbcTemplate.update(
            """
            UPDATE tb_institutions
               SET deleted_at = CURRENT_TIMESTAMP
             WHERE id = ?
            """,
            id
        );
        assertThat(updated).isEqualTo(1);
    }
}
