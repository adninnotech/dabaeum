package com.adn.dabaeum.institution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstitutionSortTest {

    @ParameterizedTest
    @CsvSource({
        "createdAt,CREATED_AT",
        "updatedAt,UPDATED_AT",
        "institutionCode,INSTITUTION_CODE",
        "name,NAME",
        "status,STATUS"
    })
    void mapsOnlyWhitelistedSortFields(
        String value,
        InstitutionSort expected
    ) {
        assertThat(InstitutionSort.fromApiValue(value)).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> InstitutionSort.fromApiValue("businessNumber"));
    }

    @Test
    void mapsOnlySupportedDirections() {
        assertThat(SortDirection.fromApiValue("asc"))
            .isEqualTo(SortDirection.ASC);
        assertThat(SortDirection.fromApiValue("desc"))
            .isEqualTo(SortDirection.DESC);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SortDirection.fromApiValue("sideways"));
    }

    @Test
    void preservesAllInstitutionFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-02T00:00:00Z");
        Instant deletedAt = Instant.parse("2026-08-03T00:00:00Z");

        Institution institution = new Institution(
            id,
            "INST-001",
            "다배움 기관",
            "123-45-67890",
            "홍길동",
            "서울시 중구",
            "02-1234-5678",
            "contact@example.com",
            InstitutionStatus.SUSPENDED,
            createdAt,
            updatedAt,
            deletedAt
        );

        assertThat(institution.id()).isEqualTo(id);
        assertThat(institution.institutionCode()).isEqualTo("INST-001");
        assertThat(institution.name()).isEqualTo("다배움 기관");
        assertThat(institution.businessNumber()).isEqualTo("123-45-67890");
        assertThat(institution.representativeName()).isEqualTo("홍길동");
        assertThat(institution.address()).isEqualTo("서울시 중구");
        assertThat(institution.contactPhone()).isEqualTo("02-1234-5678");
        assertThat(institution.contactEmail()).isEqualTo("contact@example.com");
        assertThat(institution.status()).isEqualTo(InstitutionStatus.SUSPENDED);
        assertThat(institution.createdAt()).isEqualTo(createdAt);
        assertThat(institution.updatedAt()).isEqualTo(updatedAt);
        assertThat(institution.deletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void validatesPageCriteriaBounds() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new InstitutionPageCriteria(
                -1,
                20,
                InstitutionSort.CREATED_AT,
                SortDirection.DESC
            ));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new InstitutionPageCriteria(
                0,
                0,
                InstitutionSort.CREATED_AT,
                SortDirection.DESC
            ));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new InstitutionPageCriteria(
                0,
                101,
                InstitutionSort.CREATED_AT,
                SortDirection.DESC
            ));
    }
}
