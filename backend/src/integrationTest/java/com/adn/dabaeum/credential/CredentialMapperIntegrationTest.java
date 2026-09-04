package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialQueryRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CredentialMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final String VC_PAYLOAD =
        "  {\"credentialSubject\":  { \"achievement\" : \"completed\" }}  ";

    @Autowired
    CredentialGroupRepository groupRepository;

    @Autowired
    CredentialRepository credentialRepository;

    @Autowired
    CredentialQueryRepository credentialQueryRepository;

    @Test
    void insertsFindsAndLocksOneGroupPerCompletion() {
        Fixture fixture = insertCompletionFixture();
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);

        groupRepository.insert(group);

        assertThat(groupRepository.findByCompletionId(fixture.completionId())).contains(group);
        assertThat(groupRepository.findByCompletionIdForUpdate(fixture.completionId())).contains(group);
        assertSqlState("23505", () -> groupRepository.insert(
            new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW)));
    }

    @Test
    void roundTripsCredentialUpdateAndEnforcesOneActiveCredential() {
        Fixture fixture = insertCompletionFixture();
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);
        groupRepository.insert(group);
        Credential pending = pendingCredential(group.id(), 1, null);
        credentialRepository.insert(pending);

        assertThat(credentialRepository.findById(pending.id())).contains(pending);
        assertThat(credentialRepository.findByIdForUpdate(pending.id())).contains(pending);
        assertThat(credentialRepository.nextVersionForUpdate(group.id())).isEqualTo(2);

        Credential issued = issue(pending);
        credentialRepository.update(issued);
        assertThat(credentialRepository.findActiveByGroupId(group.id())).contains(issued);
        assertThat(credentialRepository.findActiveByGroupId(group.id()).orElseThrow().vcPayload())
            .isEqualTo(VC_PAYLOAD);
        assertThat(credentialRepository.findByUserId(fixture.userId(), 10, 0, "createdAt,asc"))
            .contains(issued);
        assertThat(credentialRepository.countByUserId(fixture.userId())).isEqualTo(1);

        Credential replacement = issue(pendingCredential(group.id(), 2, issued.id()));
        assertSqlState("23505", () -> credentialRepository.insert(replacement));
    }

    @Test
    void resolvesCourseAndInstitutionForCredentialLookups() {
        Fixture fixture = insertCompletionFixture();
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);
        groupRepository.insert(group);
        Credential credential = pendingCredential(group.id(), 1, null);
        credentialRepository.insert(credential);
        CredentialCourseView expected = new CredentialCourseView(
            credential.id(), fixture.courseId(), fixture.courseTitle(),
            fixture.courseCode(), fixture.institutionName());

        assertThat(credentialQueryRepository.findCourseByCredentialId(credential.id()))
            .contains(expected);
        assertThat(credentialQueryRepository.findCoursesByCredentialIds(List.of(credential.id())))
            .containsExactly(expected);
        assertThat(credentialQueryRepository.findCoursesByCredentialIds(List.of())).isEmpty();
        assertThat(credentialQueryRepository.findCourseByCredentialId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void filtersUserCredentialsByInstitution() {
        Fixture fixture = insertCompletionFixture();
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);
        groupRepository.insert(group);
        Credential credential = pendingCredential(group.id(), 1, null);
        credentialRepository.insert(credential);
        UUID institutionId = jdbcTemplate.queryForObject(
            "SELECT institution_id FROM tb_courses WHERE id = ?", UUID.class, fixture.courseId());

        assertThat(credentialQueryRepository.findByUserIdAndInstitutionIds(
            fixture.userId(), List.of(institutionId), 10, 0, "createdAt,desc"))
            .extracting(Credential::id).containsExactly(credential.id());
        assertThat(credentialQueryRepository.countByUserIdAndInstitutionIds(
            fixture.userId(), List.of(institutionId))).isEqualTo(1);
        assertThat(credentialQueryRepository.findByUserIdAndInstitutionIds(
            fixture.userId(), List.of(UUID.randomUUID()), 10, 0, "createdAt,desc")).isEmpty();
        assertThat(credentialQueryRepository.countByUserIdAndInstitutionIds(
            fixture.userId(), List.of(UUID.randomUUID()))).isZero();
    }

    @Test
    void keepsCourseNameOnCredentialsWhoseCourseWasSoftDeleted() {
        Fixture fixture = insertCompletionFixture();
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), fixture.completionId(), NOW);
        groupRepository.insert(group);
        Credential credential = pendingCredential(group.id(), 1, null);
        credentialRepository.insert(credential);
        jdbcTemplate.update("UPDATE tb_courses SET deleted_at = ? WHERE id = ?",
            OffsetDateTime.now(), fixture.courseId());

        assertThat(credentialQueryRepository.findCourseByCredentialId(credential.id())
            .orElseThrow().courseTitle()).isEqualTo(fixture.courseTitle());
    }

    @Test
    void rejectsSortOutsideTheRepositoryAllowlist() {
        Fixture fixture = insertCompletionFixture();

        assertThatIllegalArgumentException().isThrownBy(() ->
            credentialRepository.findByUserId(fixture.userId(), 10, 0, "created_at;drop table"));
    }

    private Credential pendingCredential(UUID groupId, int versionNo, UUID previousCredentialId) {
        return new Credential(
            UUID.randomUUID(), groupId, previousCredentialId, uniqueCode("credential"), versionNo,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, null, null, null, null, null, NOW, NOW
        );
    }

    private Credential issue(Credential pending) {
        Instant issuedAt = NOW.plusSeconds(1);
        return pending.startIssuing(issuedAt).markIssued(
            VC_PAYLOAD,
            "a".repeat(64),
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            issuedAt,
            issuedAt
        );
    }

    private Fixture insertCompletionFixture() {
        UUID institutionId = UUID.randomUUID();
        String institutionName = uniqueCode("institution-name");
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), institutionName);
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        UUID courseId = UUID.randomUUID();
        String courseCode = uniqueCode("course");
        String courseTitle = uniqueCode("course-title");
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20)
            """, courseId, institutionId, courseCode, courseTitle,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, userId, OffsetDateTime.now());
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_completions (id, enrollment_id) VALUES (?, ?)",
            completionId, enrollmentId);
        return new Fixture(
            completionId, userId, courseId, courseTitle, courseCode, institutionName);
    }

    private record Fixture(
        UUID completionId,
        UUID userId,
        UUID courseId,
        String courseTitle,
        String courseCode,
        String institutionName
    ) {
    }
}
