package com.adn.dabaeum.institution;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionSort;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.institution.domain.SortDirection;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InstitutionMapperIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    InstitutionRepository repository;

    @Test
    void savesAndFindsAllNullableFields() {
        Institution institution = institution(
            InstitutionStatus.ACTIVE,
            "다배움 전체 필드 기관",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            null
        );

        repository.save(institution);

        assertThat(repository.findById(institution.id()))
            .contains(institution);
    }

    @Test
    void findActiveByIdAndCodeExcludeSoftDeletedRow() {
        Institution institution = institution(
            InstitutionStatus.ACTIVE,
            "다배움 삭제 기관",
            Instant.now().minus(2, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS),
            null
        );

        repository.save(institution);

        assertThat(repository.findActiveById(institution.id()))
            .contains(institution);
        assertThat(repository.findActiveByCode(institution.institutionCode()))
            .contains(institution);

        softDelete(institution.id());

        assertThat(repository.findById(institution.id()))
            .isPresent()
            .get()
            .extracting(Institution::deletedAt)
            .isNotNull();
        assertThat(repository.findActiveById(institution.id())).isEmpty();
        assertThat(repository.findActiveByCode(institution.institutionCode()))
            .isEmpty();
    }

    @Test
    void activePageIncludesAllStatusesAndExcludesDeletedRows() {
        Institution active = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 ACTIVE",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        Institution inactive = institution(
            InstitutionStatus.INACTIVE,
            "기관 페이지 INACTIVE",
            Instant.parse("2026-08-02T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            null
        );
        Institution suspended = institution(
            InstitutionStatus.SUSPENDED,
            "기관 페이지 SUSPENDED",
            Instant.parse("2026-08-03T00:00:00Z"),
            Instant.parse("2026-08-03T00:00:00Z"),
            null
        );
        Institution deleted = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 DELETED",
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:00Z")
        );

        List.of(active, inactive, suspended, deleted)
            .forEach(repository::save);

        List<Institution> page = repository.findActivePage(new InstitutionPageCriteria(
            0,
            100,
            InstitutionSort.INSTITUTION_CODE,
            SortDirection.ASC
        ));

        assertThat(page)
            .extracting(Institution::id)
            .contains(active.id(), inactive.id(), suspended.id())
            .doesNotContain(deleted.id());
    }

    @Test
    void appliesCreatedAtDescendingAndNameAscendingSorts() {
        Institution older = institution(
            InstitutionStatus.ACTIVE,
            "기관 정렬 Z",
            Instant.parse("2090-08-01T00:00:00Z"),
            Instant.parse("2090-08-01T00:00:00Z"),
            null
        );
        Institution newer = institution(
            InstitutionStatus.ACTIVE,
            "기관 정렬 A",
            Instant.parse("2090-08-02T00:00:00Z"),
            Instant.parse("2090-08-02T00:00:00Z"),
            null
        );
        repository.save(older);
        repository.save(newer);

        List<Institution> createdAtDesc = repository.findActivePage(
            new InstitutionPageCriteria(
                0,
                100,
                InstitutionSort.CREATED_AT,
                SortDirection.DESC
            )
        );
        assertThat(createdAtDesc.indexOf(newer))
            .isLessThan(createdAtDesc.indexOf(older));

        List<Institution> nameAsc = repository.findActivePage(
            new InstitutionPageCriteria(
                0,
                100,
                InstitutionSort.NAME,
                SortDirection.ASC
            )
        );
        assertThat(nameAsc.indexOf(newer))
            .isLessThan(nameAsc.indexOf(older));
    }

    @Test
    void appliesOffsetAndLimit() {
        Institution first = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 1",
            Instant.parse("2091-08-01T00:00:00Z"),
            Instant.parse("2091-08-01T00:00:00Z"),
            null
        );
        Institution second = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 2",
            Instant.parse("2091-08-02T00:00:00Z"),
            Instant.parse("2091-08-02T00:00:00Z"),
            null
        );
        Institution third = institution(
            InstitutionStatus.ACTIVE,
            "기관 페이지 3",
            Instant.parse("2091-08-03T00:00:00Z"),
            Instant.parse("2091-08-03T00:00:00Z"),
            null
        );
        List.of(first, second, third).forEach(repository::save);

        List<Institution> firstTwo = repository.findActivePage(
            new InstitutionPageCriteria(
                0,
                2,
                InstitutionSort.CREATED_AT,
                SortDirection.ASC
            )
        );
        List<Institution> page = repository.findActivePage(new InstitutionPageCriteria(
            1,
            1,
            InstitutionSort.CREATED_AT,
            SortDirection.ASC
        ));

        assertThat(firstTwo).hasSize(2);
        assertThat(page).containsExactly(firstTwo.get(1));
    }

    @Test
    void countActiveExcludesDeletedRows() {
        long baseline = repository.countActive();
        Institution active = institution(
            InstitutionStatus.ACTIVE,
            "기관 count active",
            Instant.now(),
            Instant.now(),
            null
        );
        Institution deleted = institution(
            InstitutionStatus.ACTIVE,
            "기관 count deleted",
            Instant.now(),
            Instant.now(),
            Instant.now()
        );

        repository.save(active);
        repository.save(deleted);

        assertThat(repository.countActive()).isEqualTo(baseline + 1);
    }

    @Test
    void updatesAllPublicFieldsAndUpdatedAt() {
        Institution original = institution(
            InstitutionStatus.ACTIVE,
            "기관 수정 전",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(original);

        Institution updated = new Institution(
            original.id(),
            original.institutionCode() + "-UPDATED",
            "기관 수정 후",
            "987-65-43210",
            "김수정",
            "부산시 해운대구",
            "051-9876-5432",
            "updated@example.com",
            InstitutionStatus.SUSPENDED,
            original.createdAt(),
            Instant.parse("2026-08-03T00:00:00Z"),
            null
        );

        assertThat(repository.updateActive(updated)).isTrue();
        assertThat(repository.findById(updated.id())).contains(updated);
    }

    @Test
    void updateActiveReturnsFalseForDeletedRow() {
        Institution original = institution(
            InstitutionStatus.ACTIVE,
            "기관 삭제 수정",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(original);
        softDelete(original.id());

        Institution updated = new Institution(
            original.id(),
            original.institutionCode(),
            "수정 불가",
            original.businessNumber(),
            original.representativeName(),
            original.address(),
            original.contactPhone(),
            original.contactEmail(),
            InstitutionStatus.INACTIVE,
            original.createdAt(),
            Instant.parse("2026-08-04T00:00:00Z"),
            original.deletedAt()
        );

        assertThat(repository.updateActive(updated)).isFalse();
    }

    private Institution institution(
        InstitutionStatus status,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        return new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            name,
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            status,
            createdAt.truncatedTo(ChronoUnit.MICROS),
            updatedAt.truncatedTo(ChronoUnit.MICROS),
            deletedAt == null ? null : deletedAt.truncatedTo(ChronoUnit.MICROS)
        );
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
