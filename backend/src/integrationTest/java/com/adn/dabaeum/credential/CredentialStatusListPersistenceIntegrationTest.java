package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bitstring Status List 의 매퍼 SQL 과 V19 제약을 실제 PostgreSQL 로 확인한다.
 *
 * <p>칸 배정은 동시에 같은 인덱스를 노릴 수 있어 유니크 제약이 최후 방어선이 된다. 그 제약이
 * 실제로 걸리는지, 전환 이전 발급분처럼 컬럼이 NULL 인 행이 그대로 남는지를 여기서 본다.
 */
class CredentialStatusListPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final String VC_HASH = "b".repeat(64);

    @Autowired
    CredentialGroupRepository groupRepository;

    @Autowired
    CredentialRepository credentialRepository;

    @Autowired
    CredentialStatusListRepository statusListRepository;

    @Test
    void roundTripsAListAndAssignsAnEntryToACredential() {
        UUID institutionId = insertInstitution();
        CredentialStatusList list = insertList(institutionId, 1);

        assertThat(statusListRepository.findById(list.id())).contains(list);
        assertThat(statusListRepository.findLatestByInstitutionId(institutionId)).contains(list);
        assertThat(statusListRepository.countEntries(list.id())).isZero();

        Credential credential = insertIssuedCredential(institutionId);
        CredentialStatusListEntry entry = new CredentialStatusListEntry(list.id(), 94_567);

        assertThat(statusListRepository.assignEntry(credential.id(), entry)).isTrue();
        assertThat(statusListRepository.findEntryByCredentialId(credential.id())).contains(entry);
        assertThat(statusListRepository.countEntries(list.id())).isEqualTo(1);
    }

    @Test
    void latestListPerInstitutionIsTheHighestListNo() {
        UUID institutionId = insertInstitution();
        insertList(institutionId, 1);
        CredentialStatusList second = insertList(institutionId, 2);

        assertThat(statusListRepository.findLatestByInstitutionId(institutionId)).contains(second);
        assertThat(statusListRepository.findLatestByInstitutionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aCredentialKeepsItsFirstEntryAndTheSameSlotCannotBeGivenTwice() {
        UUID institutionId = insertInstitution();
        CredentialStatusList list = insertList(institutionId, 1);
        Credential first = insertIssuedCredential(institutionId);
        CredentialStatusListEntry slot = new CredentialStatusListEntry(list.id(), 12);

        assertThat(statusListRepository.assignEntry(first.id(), slot)).isTrue();

        // 이미 자리가 있는 행은 다시 배정되지 않는다. 인덱스는 서명된 VC 에 박히므로 바뀌면 안 된다.
        assertThat(statusListRepository.assignEntry(
            first.id(), new CredentialStatusListEntry(list.id(), 13))).isFalse();
        assertThat(statusListRepository.findEntryByCredentialId(first.id())).contains(slot);

        // 다른 행이 같은 칸을 노리면 유니크 제약에 걸려 실패로 돌아온다.
        Credential second = insertIssuedCredential(institutionId);
        assertThat(statusListRepository.assignEntry(second.id(), slot)).isFalse();
        assertThat(statusListRepository.findEntryByCredentialId(second.id())).isEmpty();
    }

    @Test
    void revokedAndSupersededCredentialsBecomeTheSetBitsInIndexOrder() {
        UUID institutionId = insertInstitution();
        CredentialStatusList list = insertList(institutionId, 1);

        assignStatus(institutionId, list, 900, CredentialStatus.REVOKED);
        assignStatus(institutionId, list, 100, CredentialStatus.SUPERSEDED);
        assignStatus(institutionId, list, 500, CredentialStatus.ISSUED);
        assignStatus(institutionId, list, 700, CredentialStatus.EXPIRED);

        // 폐기 목적의 리스트이므로 REVOKED 와 재발급으로 대체된 SUPERSEDED 만 1 이 된다.
        assertThat(statusListRepository.findRevokedIndexes(list.id()))
            .containsExactly(100, 900);
    }

    @Test
    void credentialsIssuedBeforeTheTransitionKeepBothColumnsNull() {
        Credential legacy = insertIssuedCredential(insertInstitution());

        assertThat(statusListRepository.findEntryByCredentialId(legacy.id())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM tb_credentials
             WHERE id = ? AND status_list_id IS NULL AND status_list_index IS NULL
            """, Integer.class, legacy.id())).isEqualTo(1);
    }

    // 아래 제약 검증은 한 테스트에 하나씩만 둔다. PostgreSQL 은 실패한 문장 하나로 트랜잭션
    // 전체를 중단시키므로(25P02), 같은 트랜잭션에서 두 번째 위반을 확인할 수 없다.

    @Test
    void databaseRejectsAnIndexWithoutAList() {
        Credential credential = insertIssuedCredential(insertInstitution());

        assertSqlState("23514", () -> jdbcTemplate.update(
            "UPDATE tb_credentials SET status_list_index = 5 WHERE id = ?", credential.id()));
    }

    @Test
    void databaseRejectsAListWithoutAnIndex() {
        UUID institutionId = insertInstitution();
        CredentialStatusList list = insertList(institutionId, 1);
        Credential credential = insertIssuedCredential(institutionId);

        assertSqlState("23514", () -> jdbcTemplate.update(
            "UPDATE tb_credentials SET status_list_id = ? WHERE id = ?", list.id(), credential.id()));
    }

    @Test
    void databaseRejectsANegativeIndex() {
        UUID institutionId = insertInstitution();
        CredentialStatusList list = insertList(institutionId, 1);
        Credential credential = insertIssuedCredential(institutionId);

        assertSqlState("23514", () -> jdbcTemplate.update("""
            UPDATE tb_credentials SET status_list_id = ?, status_list_index = -1 WHERE id = ?
            """, list.id(), credential.id()));
    }

    /** 규격 최소 크기 미만인 리스트는 herd privacy 를 못 지키므로 막는다. */
    @Test
    void databaseRejectsAnUndersizedList() {
        UUID institutionId = insertInstitution();

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_credential_status_lists (id, institution_id, list_no, capacity)
            VALUES (?, ?, 9, 1024)
            """, UUID.randomUUID(), institutionId));
    }

    @Test
    void databaseRejectsADuplicateListNoWithinAnInstitution() {
        UUID institutionId = insertInstitution();
        insertList(institutionId, 1);

        assertSqlState("23505", () -> jdbcTemplate.update("""
            INSERT INTO tb_credential_status_lists (id, institution_id, list_no)
            VALUES (?, ?, 1)
            """, UUID.randomUUID(), institutionId));
    }

    private void assignStatus(
        UUID institutionId,
        CredentialStatusList list,
        int index,
        CredentialStatus status
    ) {
        Credential credential = insertIssuedCredential(institutionId);
        assertThat(statusListRepository.assignEntry(
            credential.id(), new CredentialStatusListEntry(list.id(), index))).isTrue();
        Credential moved = switch (status) {
            case REVOKED -> credential.markRevoked("administrative correction", NOW.plusSeconds(1));
            case SUPERSEDED -> credential.markSuperseded(NOW.plusSeconds(1));
            case EXPIRED -> credential.markExpired(NOW.plusSeconds(1));
            default -> credential;
        };
        if (moved != credential) {
            credentialRepository.update(moved);
        }
    }

    private CredentialStatusList insertList(UUID institutionId, int listNo) {
        CredentialStatusList list = new CredentialStatusList(UUID.randomUUID(), institutionId,
            listNo, CredentialStatusList.REVOCATION, CredentialStatusList.MINIMUM_CAPACITY, NOW);
        statusListRepository.insert(list);
        return list;
    }

    private Credential insertIssuedCredential(UUID institutionId) {
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20)
            """, courseId, institutionId, uniqueCode("course"), uniqueCode("course-title"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, userId, OffsetDateTime.now());
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_completions (id, enrollment_id) VALUES (?, ?)",
            completionId, enrollmentId);
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), completionId, NOW);
        groupRepository.insert(group);
        Credential credential = new Credential(
            UUID.randomUUID(), group.id(), null, uniqueCode("credential"), 1,
            "urn:dabaeum:institution:" + institutionId,
            "urn:dabaeum:user:" + userId,
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUED, NOW, null,
            "header.payload.signature", VC_HASH, null, null,
            NOW, null, null, null, null, NOW, NOW);
        credentialRepository.insert(credential);
        return credential;
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        return institutionId;
    }
}
