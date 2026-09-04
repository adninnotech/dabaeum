package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.BlockchainRequestPort;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialQueryRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialView;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.PendingBlockchainRequest;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CredentialApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2027-08-07T00:00:00Z");
    private static final UUID COMPLETION_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000014");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");
    private static final UUID GROUP_ID = UUID.fromString(
        "60000000-0000-0000-0000-000000000006");

    @Test
    void issuesCompletedCredentialAsPendingAndCreatesOneAnchorRequest() {
        Fakes fakes = fakes();

        Credential result = service(fakes).issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "issue-key-1", institutionAdmin(), NOW));

        assertThat(result.status()).isEqualTo(CredentialStatus.PENDING);
        assertThat(result.credentialGroupId()).isEqualTo(GROUP_ID);
        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.credentialNo()).startsWith("CERT-").hasSize(37);
        assertThat(result.validUntil()).isEqualTo(VALID_UNTIL);
        assertThat(result.chainKey()).isEqualTo("CHAINKEY00000001");
        assertThat(result.vcHashVersion()).isEqualTo("COMPACT_JWS_SHA256_V1");
        assertThat(fakes.groups.inserted).hasSize(1);
        assertThat(fakes.credentials.inserted).containsExactly(result);
        assertThat(fakes.blockchain.created).singleElement().satisfies(request -> {
            assertThat(request.credentialId()).isEqualTo(result.id());
            assertThat(request.transactionType()).isEqualTo("VC_ANCHOR");
            assertThat(request.network()).isEqualTo("FABRIC_POC");
            assertThat(request.idempotencyKey()).isEqualTo("issue-key-1");
            assertThat(request.requestHash()).hasSize(64).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void chain_key가_한_번부터_네_번까지_충돌하면_다음_키로_발급한다() {
        for (int collisionCount = 1; collisionCount <= 4; collisionCount++) {
            Fakes fakes = fakes();
            fakes.credentials.chainKeyCollisionsRemaining = collisionCount;

            Credential result = service(fakes).issue(new IssueCredentialCommand(
                COMPLETION_ID, VALID_UNTIL, "chain-key-retry-" + collisionCount,
                institutionAdmin(), NOW));

            int expectedAttempts = collisionCount + 1;
            assertThat(result.chainKey()).isEqualTo(
                "CHAINKEY" + String.format(java.util.Locale.ROOT, "%08d", expectedAttempts));
            assertThat(fakes.credentials.chainKeyInsertAttempts).isEqualTo(expectedAttempts);
            assertThat(fakes.chainKeys.generatedCount).isEqualTo(expectedAttempts);
            assertThat(fakes.credentials.inserted).containsExactly(result);
        }
    }

    @Test
    void chain_key가_다섯_번_충돌하면_명시적_state_conflict로_종료한다() {
        Fakes fakes = fakes();
        fakes.credentials.chainKeyCollisionsRemaining = 5;

        assertThatThrownBy(() -> service(fakes).issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "chain-key-exhausted", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.CREDENTIAL_STATE_CONFLICT));

        assertThat(fakes.credentials.chainKeyInsertAttempts).isEqualTo(5);
        assertThat(fakes.chainKeys.generatedCount).isEqualTo(5);
        assertThat(fakes.credentials.inserted).isEmpty();
        assertThat(fakes.blockchain.created).isEmpty();
    }

    @Test
    void chain_key가_아닌_무결성_오류는_재시도하지_않는다() {
        Fakes fakes = fakes();
        fakes.credentials.nonChainKeyFailure = new org.springframework.dao.DataIntegrityViolationException(
            "credential_no unique violation");

        assertThatThrownBy(() -> service(fakes).issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "other-integrity-error", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.CREDENTIAL_STATE_CONFLICT));

        assertThat(fakes.credentials.chainKeyInsertAttempts).isEqualTo(1);
        assertThat(fakes.chainKeys.generatedCount).isEqualTo(1);
        assertThat(fakes.credentials.inserted).isEmpty();
    }

    @Test
    void reusesExistingCredentialForSameIdempotencyRequest() {
        Fakes fakes = fakes();
        DefaultCredentialApplicationService service = service(fakes);
        Credential first = service.issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "same-key", institutionAdmin(), NOW));

        Credential second = service.issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "same-key", institutionAdmin(), NOW.plusSeconds(1)));

        assertThat(second).isEqualTo(first);
        assertThat(fakes.credentials.inserted).hasSize(1);
        assertThat(fakes.blockchain.created).hasSize(1);
    }

    @Test
    void legacyIdempotencyKeyDoesNotPolluteFabricPocIssue() {
        Fakes fakes = fakes();
        fakes.blockchain.created.add(new PendingBlockchainRequest(
            UUID.randomUUID(), UUID.randomUUID(), "VC_ANCHOR", "DABAEUM_FABRIC",
            "cross-network-key", "b".repeat(64), NOW.minusSeconds(1)));

        Credential issued = service(fakes).issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "cross-network-key", institutionAdmin(), NOW));

        assertThat(issued.chainKey()).isEqualTo("CHAINKEY00000001");
        assertThat(fakes.blockchain.created).hasSize(2);
        assertThat(fakes.blockchain.created.get(1).network()).isEqualTo("FABRIC_POC");
    }

    @Test
    void rechecksIssueAuthorizationBeforeReusingExistingIdempotencyRequest() {
        Fakes fakes = fakes();
        DefaultCredentialApplicationService service = service(fakes);
        service.issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "protected-key", institutionAdmin(), NOW));

        assertThatThrownBy(() -> service.issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "protected-key", otherLearner(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN));
    }

    @Test
    void rejectsSameIdempotencyKeyForDifferentRequestHash() {
        Fakes fakes = fakes();
        DefaultCredentialApplicationService service = service(fakes);
        service.issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "same-key", institutionAdmin(), NOW));

        assertThatThrownBy(() -> service.issue(new IssueCredentialCommand(
            COMPLETION_ID, NOW.plusSeconds(3600), "same-key", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT);
                assertThat(exception.status().value()).isEqualTo(409);
            });
    }

    @Test
    void rejectsActiveCredentialAndUnconfirmedCompletion() {
        Fakes active = fakes();
        active.groups.byCompletion.put(COMPLETION_ID,
            new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW.minusSeconds(60)));
        active.credentials.active = issuedCredential(active.groups.byCompletion.get(COMPLETION_ID));

        assertThatThrownBy(() -> service(active).issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "active-key", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.CREDENTIAL_ALREADY_EXISTS));

        Fakes incomplete = fakes();
        incomplete.completions.byId.put(COMPLETION_ID, completion(CompletionStatus.ELIGIBLE));
        assertThatThrownBy(() -> service(incomplete).issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "incomplete-key", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.COMPLETION_NOT_CONFIRMED));
    }

    @Test
    void rejectsInstructorOtherInstitutionAdminAndOtherLearnerFromIssue() {
        for (AuthenticatedUserContext actor : List.of(
            instructor(), otherInstitutionAdmin(), otherLearner())) {
            assertThatThrownBy(() -> service(fakes()).issue(new IssueCredentialCommand(
                COMPLETION_ID, VALID_UNTIL, UUID.randomUUID().toString(), actor, NOW)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN));
        }
    }

    @Test
    void revokesIssuedCredentialAsPendingFabricOperationWithoutChangingCredentialState() {
        Fakes fakes = fakes();
        Credential existing = issuedCredential(new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.groups.byCompletion.put(COMPLETION_ID,
            new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.credentials.insert(existing);
        fakes.credentials.active = existing;

        Credential result = service(fakes).revoke(new RevokeCredentialCommand(
            existing.id(), "requested by institution", "revoke-key", institutionAdmin(), NOW));

        assertThat(result.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(fakes.blockchain.created).singleElement().satisfies(request -> {
            assertThat(request.transactionType()).isEqualTo("VC_REVOKE");
            assertThat(request.operationReason()).isEqualTo("requested by institution");
        });
    }

    @Test
    void reissuesIssuedCredentialAsNewVersionAndKeepsPreviousIssuedUntilCommit() {
        Fakes fakes = fakes();
        Credential existing = issuedCredential(new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.groups.byCompletion.put(COMPLETION_ID,
            new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.credentials.insert(existing);
        fakes.credentials.active = existing;

        Credential result = service(fakes).reissue(new ReissueCredentialCommand(
            existing.id(), "corrected completion data", VALID_UNTIL, "reissue-key",
            institutionAdmin(), NOW));

        assertThat(result.status()).isEqualTo(CredentialStatus.PENDING);
        assertThat(result.previousCredentialId()).isEqualTo(existing.id());
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.chainKey()).isNull();
        assertThat(result.vcHashVersion()).isEqualTo("ENVELOPE_SHA256_V0");
        assertThat(fakes.credentials.findById(existing.id())).contains(existing);
        assertThat(fakes.blockchain.created).singleElement().satisfies(request -> {
            assertThat(request.transactionType()).isEqualTo("VC_REISSUE");
            assertThat(request.network()).isEqualTo("DABAEUM_FABRIC");
            assertThat(request.credentialId()).isEqualTo(result.id());
            assertThat(request.operationReason()).isEqualTo("corrected completion data");
        });
    }

    @Test
    void createsCurrentRegistryLifecycleOutboxAndAllocatesAReplacementChainKey() {
        for (boolean reissue : List.of(false, true)) {
            Fakes fakes = fakes();
            Credential current = currentIssuedCredential(
                new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
            fakes.groups.byCompletion.put(COMPLETION_ID,
                new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
            fakes.credentials.insert(current);
            fakes.credentials.active = current;

            Credential result = reissue
                ? service(fakes).reissue(new ReissueCredentialCommand(
                    current.id(), "current reissue", VALID_UNTIL,
                    "current-reissue", institutionAdmin(), NOW))
                : service(fakes).revoke(new RevokeCredentialCommand(
                    current.id(), "current revoke", "current-revoke",
                    institutionAdmin(), NOW));

            assertThat(fakes.blockchain.created).singleElement().satisfies(request -> {
                assertThat(request.network()).isEqualTo("FABRIC_POC");
                assertThat(request.transactionType()).isEqualTo(
                    reissue ? "VC_REISSUE" : "VC_REVOKE");
            });
            if (reissue) {
                assertThat(result.previousCredentialId()).isEqualTo(current.id());
                assertThat(result.chainKey()).isEqualTo("CHAINKEY00000001");
                assertThat(result.vcHashVersion()).isEqualTo("COMPACT_JWS_SHA256_V1");
            } else {
                assertThat(result).isEqualTo(current);
            }
        }
    }

    @Test
    void rejectsRevokeIdempotencyKeyReuseWithDifferentReason() {
        Fakes fakes = fakes();
        Credential existing = issuedCredential(new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.groups.byCompletion.put(COMPLETION_ID,
            new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.credentials.insert(existing);
        fakes.credentials.active = existing;
        DefaultCredentialApplicationService service = service(fakes);
        service.revoke(new RevokeCredentialCommand(existing.id(), "first", "revoke-key", institutionAdmin(), NOW));

        assertThatThrownBy(() -> service.revoke(new RevokeCredentialCommand(
            existing.id(), "different", "revoke-key", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.CREDENTIAL_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsASecondLifecycleOperationWhileAnotherFabricOperationIsPending() {
        Fakes fakes = fakes();
        Credential existing = issuedCredential(new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.groups.byCompletion.put(COMPLETION_ID,
            new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        fakes.credentials.insert(existing);
        fakes.credentials.active = existing;
        fakes.blockchain.pendingOperation = true;

        assertThatThrownBy(() -> service(fakes).reissue(new ReissueCredentialCommand(
            existing.id(), "second operation", VALID_UNTIL, "reissue-race", institutionAdmin(), NOW)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.CREDENTIAL_STATE_CONFLICT));
    }

    @Test
    void detailAndListAllowSubjectInstitutionAdminAndPlatformAdminOnly() {
        Fakes fakes = fakes();
        DefaultCredentialApplicationService service = service(fakes);
        Credential credential = service.issue(new IssueCredentialCommand(
            COMPLETION_ID, VALID_UNTIL, "read-key", institutionAdmin(), NOW));

        assertThat(service.get(credential.id(), learner()).credential()).isEqualTo(credential);
        assertThat(service.get(credential.id(), institutionAdmin()).credential())
            .isEqualTo(credential);
        assertThat(service.get(credential.id(), platformAdmin()).credential())
            .isEqualTo(credential);
        assertThat(service.get(credential.id(), learner()).course())
            .isEqualTo(new CredentialCourseView(
                credential.id(), COURSE_ID, "Course", "C-001", "Institution"));
        assertThat(service.listByUser(new ListUserCredentialsQuery(
            USER_ID, 0, 20, "createdAt,desc", learner())).data())
            .extracting(CredentialView::credential).containsExactly(credential);

        assertThatThrownBy(() -> service.get(credential.id(), otherLearner()))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN));
        assertThatThrownBy(() -> service.listByUser(new ListUserCredentialsQuery(
            USER_ID, 0, 20, "createdAt,desc", otherLearner())))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN));
    }

    @Test
    void institutionAdminCanReadAnEmptyUserListWhenEnrollmentScopeResolvesToTheirInstitution() {
        Fakes fakes = fakes();
        fakes.enrollments.institutionIdsByUser.put(OTHER_USER_ID, List.of(INSTITUTION_ID));

        CredentialPage page = service(fakes).listByUser(new ListUserCredentialsQuery(
            OTHER_USER_ID, 0, 20, "createdAt,desc", institutionAdmin()));

        assertThat(page.data()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // 학습자가 두 기관의 과정을 이수했을 때, 기관 관리자는 자기 기관 건만 보고
    // 타 기관 건 때문에 목록 전체가 거부되지 않아야 한다.
    @Test
    void institutionAdminSeesOnlyOwnInstitutionCredentialsWhenSubjectHasMixedInstitutions() {
        Fakes fakes = fakes();
        UUID otherInstitutionId = UUID.randomUUID();
        UUID otherCourseId = UUID.randomUUID();
        UUID otherEnrollmentId = UUID.randomUUID();
        UUID otherCompletionId = UUID.randomUUID();
        fakes.courses.byId.put(otherCourseId, course(otherCourseId, otherInstitutionId));
        fakes.enrollments.byId.put(otherEnrollmentId, enrollment(otherEnrollmentId, otherCourseId));
        fakes.completions.byId.put(otherCompletionId, completion(otherCompletionId, otherEnrollmentId));

        CredentialGroup ownGroup = new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW);
        CredentialGroup otherGroup = new CredentialGroup(UUID.randomUUID(), otherCompletionId, NOW);
        fakes.groups.insert(ownGroup);
        fakes.groups.insert(otherGroup);
        Credential ownCredential = issuedCredential(ownGroup);
        Credential otherCredential = issuedCredential(otherGroup);
        fakes.credentials.insert(ownCredential);
        fakes.credentials.insert(otherCredential);
        DefaultCredentialApplicationService service = service(fakes);

        ListUserCredentialsQuery asOwnAdmin = new ListUserCredentialsQuery(
            USER_ID, 0, 20, "createdAt,desc",
            context(UUID.randomUUID(), new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
        assertThat(service.listByUser(asOwnAdmin).data())
            .extracting(view -> view.credential().id()).containsExactly(ownCredential.id());
        assertThat(service.listByUser(asOwnAdmin).totalElements()).isEqualTo(1);

        ListUserCredentialsQuery asOtherAdmin = new ListUserCredentialsQuery(
            USER_ID, 0, 20, "createdAt,desc",
            context(UUID.randomUUID(), new AuthenticatedRole("INSTITUTION_ADMIN", otherInstitutionId)));
        assertThat(service.listByUser(asOtherAdmin).data())
            .extracting(view -> view.credential().id()).containsExactly(otherCredential.id());

        assertThat(service.listByUser(new ListUserCredentialsQuery(
            USER_ID, 0, 20, "createdAt,desc", platformAdmin())).data()).hasSize(2);
        assertThat(service.listByUser(new ListUserCredentialsQuery(
            USER_ID, 0, 20, "createdAt,desc", learner())).data()).hasSize(2);
    }

    private Course course(UUID id, UUID institutionId) {
        return new Course(id, institutionId, "C-" + id.toString().substring(0, 8), "Course", null,
            "category", CourseEducationType.ONLINE, LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31), null, null, 30, null, null, true, new BigDecimal("3.00"),
            CourseStatus.COMPLETED, NOW.minusSeconds(3600), NOW.minusSeconds(60), null);
    }

    private Enrollment enrollment(UUID id, UUID courseId) {
        return new Enrollment(id, courseId, USER_ID, USER_ID,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW.minusSeconds(3600),
            NOW.minusSeconds(1800), null, null, null, null, null,
            NOW.minusSeconds(3600), NOW.minusSeconds(1800));
    }

    private Completion completion(UUID id, UUID enrollmentId) {
        return new Completion(id, enrollmentId, CompletionStatus.COMPLETED,
            new BigDecimal("90.00"), 1200, new BigDecimal("3.00"), NOW, NOW, USER_ID, NOW,
            null, NOW.minusSeconds(60), NOW.minusSeconds(60));
    }

    private Fakes fakes() {
        Fakes fakes = new Fakes();
        fakes.completions.byId.put(COMPLETION_ID, completion(CompletionStatus.COMPLETED));
        fakes.enrollments.byId.put(ENROLLMENT_ID, enrollment());
        fakes.courses.byId.put(COURSE_ID, course());
        return fakes;
    }

    private DefaultCredentialApplicationService service(Fakes fakes) {
        return new DefaultCredentialApplicationService(
            fakes.completions, fakes.enrollments, fakes.courses, fakes.groups,
            fakes.credentials, fakes.credentialQueries, fakes.blockchain,
            new AuthorizationPolicy(),
            () -> GROUP_ID,
            () -> UUID.fromString("70000000-0000-0000-0000-000000000007"),
            id -> "CERT-" + id.toString().replace("-", "").toUpperCase(),
            fakes.chainKeys::generate,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private Completion completion(CompletionStatus status) {
        if (status == CompletionStatus.COMPLETED) {
            return new Completion(COMPLETION_ID, ENROLLMENT_ID, status,
                new BigDecimal("90.00"), 1200, new BigDecimal("3.00"), NOW, NOW, USER_ID, NOW,
                null, NOW.minusSeconds(60), NOW.minusSeconds(60));
        }
        return new Completion(COMPLETION_ID, ENROLLMENT_ID, status,
            new BigDecimal("90.00"), 1200, new BigDecimal("3.00"), NOW, null, null, null,
            null, NOW.minusSeconds(60), NOW.minusSeconds(60));
    }

    private Enrollment enrollment() {
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID, USER_ID,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW.minusSeconds(3600),
            NOW.minusSeconds(1800), null, null, null, null, null,
            NOW.minusSeconds(3600), NOW.minusSeconds(1800));
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "C-001", "Course", null, "category",
            CourseEducationType.ONLINE, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
            null, null, 30, null, null, true, new BigDecimal("3.00"),
            CourseStatus.COMPLETED, NOW.minusSeconds(3600), NOW.minusSeconds(60), null);
    }

    private Credential issuedCredential(CredentialGroup group) {
        return new Credential(UUID.randomUUID(), group.id(), null, "CERT-EXISTING", 1,
            "urn:dabaeum:institution:" + INSTITUTION_ID,
            "urn:dabaeum:user:" + USER_ID, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUED,
            NOW, null, "{\"credentialSubject\":{}}", "a".repeat(64), NOW, null, null, null,
            null, NOW, NOW);
    }

    private Credential currentIssuedCredential(CredentialGroup group) {
        return new Credential(UUID.randomUUID(), group.id(), null, "CERT-CURRENT", 1,
            "urn:dabaeum:institution:" + INSTITUTION_ID,
            "urn:dabaeum:user:" + USER_ID, "LIFELONG_EDUCATION_COMPLETION",
            CredentialStatus.ISSUED, NOW, null, "{\"credentialSubject\":{}}",
            "a".repeat(64), "CURRENTKEY000001", "COMPACT_JWS_SHA256_V1",
            NOW, null, null, null, null, NOW, NOW);
    }

    private AuthenticatedUserContext institutionAdmin() {
        return context(USER_ID, new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID));
    }

    private AuthenticatedUserContext platformAdmin() {
        return context(UUID.randomUUID(), new AuthenticatedRole("PLATFORM_ADMIN", null));
    }

    private AuthenticatedUserContext instructor() {
        return context(USER_ID, new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID));
    }

    private AuthenticatedUserContext otherInstitutionAdmin() {
        return context(UUID.randomUUID(), new AuthenticatedRole("INSTITUTION_ADMIN", UUID.randomUUID()));
    }

    private AuthenticatedUserContext learner() {
        return context(USER_ID, new AuthenticatedRole("LEARNER", null));
    }

    private AuthenticatedUserContext otherLearner() {
        return context(UUID.randomUUID(), new AuthenticatedRole("LEARNER", null));
    }

    private AuthenticatedUserContext context(UUID userId, AuthenticatedRole role) {
        return new AuthenticatedUserContext(userId, "LOCAL", Set.of(role));
    }

    private static final class Fakes {
        private final FakeCompletionRepository completions = new FakeCompletionRepository();
        private final FakeEnrollmentRepository enrollments = new FakeEnrollmentRepository();
        private final FakeCourseRepository courses = new FakeCourseRepository();
        private final FakeCredentialGroupRepository groups = new FakeCredentialGroupRepository();
        private final FakeCredentialRepository credentials = new FakeCredentialRepository();
        private final FakeCredentialQueryRepository credentialQueries =
            new FakeCredentialQueryRepository(this);
        private final FakeBlockchainRequestPort blockchain = new FakeBlockchainRequestPort();
        private final FakeChainKeyGenerator chainKeys = new FakeChainKeyGenerator();
    }

    private static final class FakeCredentialQueryRepository implements CredentialQueryRepository {

        private final Fakes fakes;

        private FakeCredentialQueryRepository(Fakes fakes) {
            this.fakes = fakes;
        }

        @Override
        public List<Credential> findByUserIdAndInstitutionIds(
            UUID userId, List<UUID> institutionIds, int limit, int offset, String sort
        ) {
            return fakes.credentials.findByUserId(userId, 10_000, 0, sort).stream()
                .filter(credential -> institutionIds.contains(institutionOf(credential)))
                .skip(offset).limit(limit).toList();
        }

        @Override
        public long countByUserIdAndInstitutionIds(UUID userId, List<UUID> institutionIds) {
            return findByUserIdAndInstitutionIds(userId, institutionIds, 10_000, 0, "createdAt,desc").size();
        }

        // 수료증 → 그룹 → 이수 → 수강신청 → 과정 → 기관. 실제 SQL 조인과 같은 경로다.
        private UUID institutionOf(Credential credential) {
            CredentialGroup group = fakes.groups.findById(credential.credentialGroupId()).orElseThrow();
            Completion completion = fakes.completions.findById(group.completionId()).orElseThrow();
            Enrollment enrollment = fakes.enrollments.findById(completion.enrollmentId()).orElseThrow();
            return fakes.courses.findById(enrollment.courseId()).orElseThrow().institutionId();
        }

        @Override
        public Optional<CredentialCourseView> findCourseByCredentialId(UUID credentialId) {
            return Optional.of(view(credentialId));
        }

        @Override
        public List<CredentialCourseView> findCoursesByCredentialIds(List<UUID> credentialIds) {
            return credentialIds.stream().map(FakeCredentialQueryRepository::view).toList();
        }

        private static CredentialCourseView view(UUID credentialId) {
            return new CredentialCourseView(
                credentialId, COURSE_ID, "Course", "C-001", "Institution");
        }
    }

    private static final class FakeCompletionRepository implements CompletionRepository {
        private final Map<UUID, Completion> byId = new HashMap<>();

        @Override public void save(Completion completion) { throw new UnsupportedOperationException(); }
        @Override public Optional<Completion> findByEnrollmentId(UUID enrollmentId) {
            return byId.values().stream().filter(value -> value.enrollmentId().equals(enrollmentId))
                .findFirst();
        }
        @Override public Optional<Completion> findByEnrollmentIdForUpdate(UUID enrollmentId) {
            return findByEnrollmentId(enrollmentId);
        }
        public Optional<Completion> findById(UUID completionId) { return Optional.ofNullable(byId.get(completionId)); }
        public Optional<Completion> findByIdForUpdate(UUID completionId) { return findById(completionId); }
        @Override public boolean updateEvaluation(Completion completion, com.adn.dabaeum.completion.domain.CompletionStatus expectedStatus) { throw new UnsupportedOperationException(); }
        @Override public boolean confirm(Completion completion, com.adn.dabaeum.completion.domain.CompletionStatus expectedStatus) { throw new UnsupportedOperationException(); }
        @Override public boolean revertConfirmation(Completion completion, com.adn.dabaeum.completion.domain.CompletionStatus expectedStatus) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeEnrollmentRepository implements EnrollmentRepository {
        private final Map<UUID, Enrollment> byId = new HashMap<>();
        private final Map<UUID, List<UUID>> institutionIdsByUser = new HashMap<>();
        @Override public void save(Enrollment enrollment) { throw new UnsupportedOperationException(); }
        @Override public Optional<Enrollment> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Enrollment> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public List<UUID> findInstitutionIdsByUserId(UUID userId) {
            return institutionIdsByUser.getOrDefault(userId, List.of());
        }
        @Override public Optional<Enrollment> findActiveByCourseIdAndUserId(UUID courseId, UUID userId) { throw new UnsupportedOperationException(); }
        @Override public List<Enrollment> findPageByCourseId(EnrollmentPageCriteria criteria) { throw new UnsupportedOperationException(); }
        @Override public long countByCourseId(UUID courseId) { throw new UnsupportedOperationException(); }
        @Override public long countApprovedByCourseId(UUID courseId) { throw new UnsupportedOperationException(); }
        @Override public boolean updateState(Enrollment updated, EnrollmentStatus expectedStatus) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeCourseRepository implements CourseRepository {
        private final Map<UUID, Course> byId = new HashMap<>();
        @Override public void save(Course course) { throw new UnsupportedOperationException(); }
        @Override public Optional<Course> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Course> findActiveById(UUID id) { return findById(id); }
        @Override public Optional<Course> findActiveByIdForUpdate(UUID id) { return findById(id); }
        @Override public Optional<Course> findActiveByInstitutionAndCode(UUID institutionId, String courseCode) { throw new UnsupportedOperationException(); }
        @Override public List<Course> findActivePage(CoursePageCriteria criteria) { throw new UnsupportedOperationException(); }
        @Override public long countActive() { throw new UnsupportedOperationException(); }
        @Override public boolean updateActive(Course course) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeCredentialGroupRepository implements CredentialGroupRepository {
        private final Map<UUID, CredentialGroup> byCompletion = new HashMap<>();
        private final List<CredentialGroup> inserted = new ArrayList<>();
        @Override public Optional<CredentialGroup> findById(UUID groupId) {
            return byCompletion.values().stream().filter(value -> value.id().equals(groupId)).findFirst();
        }
        @Override public Optional<CredentialGroup> findByCompletionId(UUID completionId) { return Optional.ofNullable(byCompletion.get(completionId)); }
        @Override public Optional<CredentialGroup> findByCompletionIdForUpdate(UUID completionId) { return findByCompletionId(completionId); }
        @Override public void insert(CredentialGroup group) { byCompletion.put(group.completionId(), group); inserted.add(group); }
    }

    private static final class FakeCredentialRepository implements CredentialRepository {
        private final Map<UUID, Credential> byId = new HashMap<>();
        private final List<Credential> inserted = new ArrayList<>();
        private Credential active;
        private int chainKeyCollisionsRemaining;
        private int chainKeyInsertAttempts;
        private DataIntegrityViolationException nonChainKeyFailure;
        @Override public Optional<Credential> findById(UUID credentialId) { return Optional.ofNullable(byId.get(credentialId)); }
        @Override public Optional<Credential> findByCredentialNo(String credentialNo) { return Optional.empty(); }
        @Override public Optional<Credential> findByCredentialHash(String credentialHash) { return Optional.empty(); }
        @Override public Optional<Credential> findByIdForUpdate(UUID credentialId) { return findById(credentialId); }
        @Override public Optional<Credential> findActiveByGroupId(UUID groupId) { return Optional.ofNullable(active); }
        @Override public List<Credential> findByUserId(UUID userId, int limit, int offset, String sort) { return List.copyOf(inserted); }
        @Override public long countByUserId(UUID userId) { return findByUserId(userId, 100, 0, "createdAt,desc").size(); }
        @Override public int nextVersionForUpdate(UUID groupId) {
            return byId.values().stream().filter(value -> value.credentialGroupId().equals(groupId))
                .mapToInt(Credential::versionNo).max().orElse(0) + 1;
        }
        @Override public void insert(Credential credential) { byId.put(credential.id(), credential); inserted.add(credential); }
        @Override public boolean insertIfChainKeyAvailable(Credential credential) {
            chainKeyInsertAttempts++;
            if (nonChainKeyFailure != null) {
                throw nonChainKeyFailure;
            }
            if (chainKeyCollisionsRemaining > 0) {
                chainKeyCollisionsRemaining--;
                return false;
            }
            insert(credential);
            return true;
        }
        @Override public void update(Credential credential) { byId.put(credential.id(), credential); }
    }

    private static final class FakeChainKeyGenerator {
        private int generatedCount;

        private String generate() {
            generatedCount++;
            return "CHAINKEY" + String.format(java.util.Locale.ROOT, "%08d", generatedCount);
        }
    }

    private static final class FakeBlockchainRequestPort implements BlockchainRequestPort {
        private final List<PendingBlockchainRequest> created = new ArrayList<>();
        private boolean pendingOperation;
        @Override public Optional<PendingBlockchainRequest> findByIdempotencyKey(
            String network,
            String idempotencyKey
        ) {
            return created.stream()
                .filter(value -> value.network().equals(network))
                .filter(value -> value.idempotencyKey().equals(idempotencyKey))
                .findFirst();
        }
        @Override public void createAnchor(PendingBlockchainRequest request) { created.add(request); }
        @Override public boolean hasPendingOperationForGroup(UUID credentialGroupId) { return pendingOperation; }
    }
}
