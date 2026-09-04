package com.adn.dabaeum.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpenApiOperationCoverageTest {

    record ExpectedOperation(
        PathItem.HttpMethod method,
        String path,
        String operationId,
        String successStatus
    ) {
    }

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    private static final List<ExpectedOperation> EXPECTED = List.of(
        op(PathItem.HttpMethod.GET, "/system/ping", "getSystemPing", "200"),
        op(PathItem.HttpMethod.GET, "/institutions", "listInstitutions", "200"),
        op(PathItem.HttpMethod.POST, "/institutions", "createInstitution", "201"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}", "getInstitution", "200"),
        op(PathItem.HttpMethod.PUT, "/institutions/{institutionId}", "updateInstitution", "200"),
        op(PathItem.HttpMethod.POST, "/institutions/{institutionId}/instructor-applications", "applyInstructor", "201"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}/instructor-applications", "listInstitutionInstructorApplications", "200"),
        op(PathItem.HttpMethod.GET, "/instructor-applications/me", "listMyInstructorApplications", "200"),
        op(PathItem.HttpMethod.GET, "/instructor-applications/{applicationId}", "getInstructorApplication", "200"),
        op(PathItem.HttpMethod.POST, "/instructor-applications/{applicationId}/approve", "approveInstructorApplication", "200"),
        op(PathItem.HttpMethod.POST, "/instructor-applications/{applicationId}/reject", "rejectInstructorApplication", "200"),
        op(PathItem.HttpMethod.GET, "/users", "listUsers", "200"),
        op(PathItem.HttpMethod.POST, "/users", "createUser", "201"),
        op(PathItem.HttpMethod.POST, "/auth/signup", "signupLocalAccount", "201"),
        op(PathItem.HttpMethod.POST, "/auth/login", "loginLocalAccount", "200"),
        op(PathItem.HttpMethod.GET, "/auth/dadaegu/config", "getDadaeguLoginConfig", "200"),
        op(PathItem.HttpMethod.POST, "/auth/dadaegu/login", "loginWithDadaegu", "200"),
        op(PathItem.HttpMethod.GET, "/auth/session", "getAuthSession", "200"),
        op(PathItem.HttpMethod.GET, "/users/me", "getCurrentUser", "200"),
        op(PathItem.HttpMethod.PUT, "/users/me", "updateCurrentUser", "200"),
        op(PathItem.HttpMethod.GET, "/users/{userId}", "getUser", "200"),
        op(PathItem.HttpMethod.PUT, "/users/{userId}", "updateUser", "200"),
        op(PathItem.HttpMethod.PATCH, "/users/{userId}/status", "changeUserStatus", "200"),
        op(PathItem.HttpMethod.GET, "/users/{userId}/identities", "listUserIdentities", "200"),
        op(PathItem.HttpMethod.POST, "/users/{userId}/identities", "linkUserIdentity", "201"),
        op(PathItem.HttpMethod.DELETE, "/users/{userId}/identities/{identityId}", "unlinkUserIdentity", "200"),
        op(PathItem.HttpMethod.POST, "/users/me/identities/dadaegu", "linkMyDadaeguIdentity", "201"),
        op(PathItem.HttpMethod.POST, "/users/me/identities/local", "linkMyLocalIdentity", "201"),
        op(PathItem.HttpMethod.GET, "/users/{userId}/roles", "listUserRoles", "200"),
        op(PathItem.HttpMethod.POST, "/users/{userId}/roles", "assignUserRole", "201"),
        op(PathItem.HttpMethod.DELETE, "/users/{userId}/roles/{roleId}", "revokeUserRole", "200"),
        op(PathItem.HttpMethod.GET, "/courses", "listCourses", "200"),
        op(PathItem.HttpMethod.POST, "/courses", "createCourse", "201"),
        op(PathItem.HttpMethod.GET, "/courses/{courseId}", "getCourse", "200"),
        op(PathItem.HttpMethod.PUT, "/courses/{courseId}", "updateCourse", "200"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/publish", "publishCourse", "200"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/close", "closeCourse", "200"),
        // 관리자 정정 API
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/reopen", "reopenCourse", "200"),
        op(PathItem.HttpMethod.POST, "/sessions/{sessionId}/reopen", "reopenCourseSession", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/completion/revert",
            "revertCompletionConfirmation", "200"),
        op(PathItem.HttpMethod.GET, "/courses/{courseId}/instructors", "listCourseInstructors", "200"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/instructors", "assignCourseInstructor", "201"),
        op(PathItem.HttpMethod.PUT, "/courses/{courseId}/instructors/{userId}", "updateCourseInstructor", "200"),
        op(PathItem.HttpMethod.DELETE, "/courses/{courseId}/instructors/{userId}", "removeCourseInstructor", "200"),
        op(PathItem.HttpMethod.GET, "/courses/{courseId}/sessions", "listCourseSessions", "200"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/sessions", "createCourseSession", "201"),
        op(PathItem.HttpMethod.GET, "/sessions/{sessionId}", "getCourseSession", "200"),
        op(PathItem.HttpMethod.PUT, "/sessions/{sessionId}", "updateCourseSession", "200"),
        op(PathItem.HttpMethod.GET, "/courses/{courseId}/enrollments", "listCourseEnrollments", "200"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/enrollments", "createEnrollment", "201"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/proxy-enrollments", "createProxyEnrollment", "201"),
        op(PathItem.HttpMethod.GET, "/enrollments/{enrollmentId}", "getEnrollment", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/approve", "approveEnrollment", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/reject", "rejectEnrollment", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/cancel", "cancelEnrollment", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/withdraw", "withdrawEnrollment", "200"),
        op(PathItem.HttpMethod.POST, "/sessions/{sessionId}/qr-token", "issueAttendanceQrToken", "201"),
        op(PathItem.HttpMethod.GET, "/sessions/{sessionId}/attendance", "listSessionAttendance", "200"),
        op(PathItem.HttpMethod.POST, "/sessions/{sessionId}/attendance", "recordAttendance", "201"),
        op(PathItem.HttpMethod.GET, "/attendance/{attendanceId}", "getAttendance", "200"),
        op(PathItem.HttpMethod.PATCH, "/attendance/{attendanceId}", "adjustAttendance", "200"),
        op(PathItem.HttpMethod.GET, "/enrollments/{enrollmentId}/attendance-summary", "getAttendanceSummary", "200"),
        op(PathItem.HttpMethod.GET, "/enrollments/{enrollmentId}/completion", "getCompletion", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/completion/evaluate", "evaluateCompletion", "200"),
        op(PathItem.HttpMethod.POST, "/enrollments/{enrollmentId}/completion/confirm", "confirmCompletion", "200"),
        op(PathItem.HttpMethod.GET, "/vc/contexts/lifelong-education/v1", "getLifelongEducationContextV1", "200"),
        op(PathItem.HttpMethod.GET, "/vc/vocabulary/lifelong-education/v1", "getLifelongEducationVocabularyV1", "200"),
        op(PathItem.HttpMethod.GET, "/vc/issuers/{institutionId}", "getCredentialIssuer", "200"),
        op(PathItem.HttpMethod.GET, "/vc/status/{credentialNo}", "getPublicCredentialStatus", "200"),
        op(PathItem.HttpMethod.GET, "/vc/status-lists/{listId}", "getCredentialStatusList", "200"),
        op(PathItem.HttpMethod.POST, "/completions/{completionId}/credentials", "issueCredential", "202"),
        op(PathItem.HttpMethod.GET, "/credentials/{credentialId}", "getCredential", "200"),
        op(PathItem.HttpMethod.GET, "/credentials/{credentialId}/document", "downloadCredentialDocument", "200"),
        op(PathItem.HttpMethod.GET, "/users/{userId}/credentials", "listUserCredentials", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/credentials", "listCurrentUserCredentials", "200"),
        op(PathItem.HttpMethod.POST, "/credentials/{credentialId}/revoke", "revokeCredential", "202"),
        op(PathItem.HttpMethod.POST, "/credentials/{credentialId}/reissue", "reissueCredential", "202"),
        op(PathItem.HttpMethod.POST, "/credentials/verify", "verifyCredential", "200"),
        op(PathItem.HttpMethod.GET, "/credentials/{credentialId}/verifications", "listCredentialVerifications", "200"),
        op(PathItem.HttpMethod.POST, "/credentials/{credentialId}/badges", "issueLearningBadge", "202"),
        op(PathItem.HttpMethod.GET, "/users/{userId}/badges", "listUserBadges", "200"),
        op(PathItem.HttpMethod.GET, "/badges/{badgeId}", "getLearningBadge", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/enrollments", "listMyEnrollments", "200"),
        op(PathItem.HttpMethod.GET, "/instructors/me/courses", "listInstructorCourses", "200"),
        op(PathItem.HttpMethod.GET, "/instructors/me/courses/stats", "getInstructorCourseStats", "200"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}/courses", "listInstitutionCourses", "200"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}/enrollments", "listInstitutionEnrollments", "200"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}/instructors", "listInstitutionInstructors", "200"),
        op(PathItem.HttpMethod.GET, "/notices", "listNotices", "200"),
        op(PathItem.HttpMethod.GET, "/notices/{noticeId}", "getNotice", "200"),
        op(PathItem.HttpMethod.GET, "/admin/notices", "listAdminNotices", "200"),
        op(PathItem.HttpMethod.POST, "/admin/notices", "createNotice", "201"),
        op(PathItem.HttpMethod.PUT, "/admin/notices/{noticeId}", "updateNotice", "200"),
        op(PathItem.HttpMethod.DELETE, "/admin/notices/{noticeId}", "deleteNotice", "200"),
        op(PathItem.HttpMethod.GET, "/support/faqs", "listFaqs", "200"),
        op(PathItem.HttpMethod.GET, "/contents/terms", "getTermsContent", "200"),
        op(PathItem.HttpMethod.GET, "/codes", "listCommonCodes", "200"),
        op(PathItem.HttpMethod.POST, "/inquiries", "createInquiry", "201"),
        op(PathItem.HttpMethod.GET, "/inquiries/{inquiryId}", "getInquiry", "200"),
        op(PathItem.HttpMethod.POST, "/inquiries/{inquiryId}/reply", "replyInquiry", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/inquiries", "listMyInquiries", "200"),
        op(PathItem.HttpMethod.GET, "/instructors/me/inquiries", "listInstructorInquiries", "200"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}/inquiries", "listInstitutionInquiries", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/reviews", "listMyReviews", "200"),
        op(PathItem.HttpMethod.GET, "/courses/{courseId}/reviews", "listCourseReviews", "200"),
        op(PathItem.HttpMethod.POST, "/courses/{courseId}/reviews", "createCourseReview", "201"),
        op(PathItem.HttpMethod.GET, "/users/me/interests", "listMyInterests", "200"),
        op(PathItem.HttpMethod.POST, "/users/me/interests", "addCourseInterest", "201"),
        op(PathItem.HttpMethod.DELETE, "/users/me/interests/{interestId}", "removeCourseInterest", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/notifications", "listMyNotifications", "200"),
        op(PathItem.HttpMethod.POST, "/users/me/notifications/{notificationId}/read", "readNotification", "200"),
        op(PathItem.HttpMethod.GET, "/auth/email/availability", "checkEmailAvailability", "200"),
        op(PathItem.HttpMethod.POST, "/auth/password/reset-request", "requestPasswordReset", "202"),
        op(PathItem.HttpMethod.POST, "/auth/password/reset", "confirmPasswordReset", "200"),
        op(PathItem.HttpMethod.POST, "/users/{userId}/password/reset", "adminResetPassword", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/learning-summary", "getLearningSummary", "200"),
        op(PathItem.HttpMethod.GET, "/users/me/learning-courses", "listLearningCourses", "200"),
        op(PathItem.HttpMethod.GET, "/instructors/me/enrollment-status", "listInstructorEnrollmentStatus", "200"),
        op(PathItem.HttpMethod.GET, "/enrollments/{enrollmentId}/progress", "getEnrollmentProgress", "200"),
        op(PathItem.HttpMethod.POST, "/institution-applications", "applyInstitutionJoin", "201"),
        op(PathItem.HttpMethod.GET, "/institution-applications", "listInstitutionJoinApplications", "200"),
        op(PathItem.HttpMethod.GET, "/institution-applications/{applicationId}", "getInstitutionJoinApplication", "200"),
        op(PathItem.HttpMethod.POST, "/institution-applications/{applicationId}/approve", "approveInstitutionJoinApplication", "200"),
        op(PathItem.HttpMethod.POST, "/institution-applications/{applicationId}/reject", "rejectInstitutionJoinApplication", "200"),
        op(PathItem.HttpMethod.GET, "/admin/dashboard", "getPlatformDashboard", "200"),
        op(PathItem.HttpMethod.GET, "/institutions/{institutionId}/dashboard", "getInstitutionDashboard", "200"),
        op(PathItem.HttpMethod.PATCH, "/institutions/{institutionId}/instructors/{userId}", "updateInstitutionInstructorMemo", "200"),
        op(PathItem.HttpMethod.GET, "/blockchain/metrics", "getBlockchainMetrics", "200"),
        op(PathItem.HttpMethod.GET, "/blockchain/transactions", "listBlockchainTransactions", "200"),
        op(PathItem.HttpMethod.GET, "/blockchain/alerts", "listBlockchainAlerts", "200"),
        op(PathItem.HttpMethod.POST, "/files", "uploadFile", "201"),
        op(PathItem.HttpMethod.GET, "/files/{fileId}/content", "downloadFileContent", "200")
    );

    private static final Map<String, String> REQUEST_SCHEMAS = Map.ofEntries(
        Map.entry("reopenCourse", "CorrectionReasonRequest"),
        Map.entry("reopenCourseSession", "CorrectionReasonRequest"),
        Map.entry("revertCompletionConfirmation", "CorrectionReasonRequest"),
        Map.entry("createInstitution", "InstitutionCreateRequest"),
        Map.entry("createUser", "UserCreateRequest"),
        Map.entry("signupLocalAccount", "SignupRequest"),
        Map.entry("loginLocalAccount", "LoginRequest"),
        Map.entry("loginWithDadaegu", "DadaeguLoginRequest"),
        Map.entry("updateCurrentUser", "UserMeUpdateRequest"),
        Map.entry("updateUser", "UserUpdateRequest"),
        Map.entry("changeUserStatus", "UserStatusChangeRequest"),
        Map.entry("linkUserIdentity", "IdentityLinkRequest"),
        Map.entry("linkMyDadaeguIdentity", "DadaeguLoginRequest"),
        Map.entry("linkMyLocalIdentity", "LocalIdentityLinkRequest"),
        Map.entry("assignUserRole", "RoleAssignmentRequest"),
        Map.entry("updateInstitution", "InstitutionUpdateRequest"),
        Map.entry("applyInstructor", "InstructorApplicationRequest"),
        Map.entry("rejectInstructorApplication", "InstructorApplicationRejectRequest"),
        Map.entry("assignCourseInstructor", "CourseInstructorAssignRequest"),
        Map.entry("updateCourseInstructor", "CourseInstructorUpdateRequest"),
        Map.entry("createCourse", "CourseCreateRequest"),
        Map.entry("updateCourse", "CourseUpdateRequest"),
        Map.entry("createCourseSession", "CourseSessionCreateRequest"),
        Map.entry("updateCourseSession", "CourseSessionUpdateRequest"),
        Map.entry("createEnrollment", "EnrollmentCreateRequest"),
        Map.entry("createProxyEnrollment", "ProxyEnrollmentCreateRequest"),
        Map.entry("rejectEnrollment", "EnrollmentRejectionRequest"),
        Map.entry("recordAttendance", "AttendanceCreateRequest"),
        Map.entry("adjustAttendance", "AttendanceAdjustmentRequest"),
        Map.entry("evaluateCompletion", "CompletionEvaluationRequest"),
        Map.entry("issueCredential", "CredentialIssueRequest"),
        Map.entry("revokeCredential", "CredentialRevokeRequest"),
        Map.entry("reissueCredential", "CredentialReissueRequest"),
        Map.entry("verifyCredential", "CredentialVerifyRequest"),
        Map.entry("issueLearningBadge", "BadgeIssueRequest"),
        Map.entry("createNotice", "NoticeCreateRequest"),
        Map.entry("updateNotice", "NoticeUpdateRequest"),
        Map.entry("createInquiry", "InquiryCreateRequest"),
        Map.entry("replyInquiry", "InquiryReplyRequest"),
        Map.entry("createCourseReview", "ReviewCreateRequest"),
        Map.entry("addCourseInterest", "InterestCreateRequest"),
        Map.entry("requestPasswordReset", "PasswordResetRequestRequest"),
        Map.entry("confirmPasswordReset", "PasswordResetConfirmRequest"),
        Map.entry("adminResetPassword", "AdminPasswordResetRequest"),
        Map.entry("applyInstitutionJoin", "InstitutionJoinApplicationCreateRequest"),
        Map.entry("rejectInstitutionJoinApplication", "InstitutionJoinApplicationRejectRequest"),
        Map.entry("updateInstitutionInstructorMemo", "InstructorMemoUpdateRequest"),
        Map.entry("uploadFile", "FileUploadRequest")
    );

    private static final Set<String> PUBLIC_OPERATIONS =
        Set.of("getSystemPing", "signupLocalAccount", "loginLocalAccount",
            "getDadaeguLoginConfig", "loginWithDadaegu",
            "verifyCredential", "getLifelongEducationContextV1",
            "getLifelongEducationVocabularyV1", "getCredentialIssuer",
            "getPublicCredentialStatus", "getCredentialStatusList",
            "listNotices", "getNotice", "listFaqs", "getTermsContent",
            "listCommonCodes", "checkEmailAvailability", "requestPasswordReset",
            "confirmPasswordReset", "downloadFileContent");

    private static final Set<String> RAW_SUCCESS_OPERATIONS = Set.of(
        "getLifelongEducationContextV1", "getLifelongEducationVocabularyV1",
        "getCredentialIssuer", "getPublicCredentialStatus", "getCredentialStatusList",
        "downloadCredentialDocument",
        "downloadFileContent");

    /** 파일 업로드만 multipart 본문을 사용한다. 그 외 요청 본문은 전부 JSON이다. */
    private static final Set<String> MULTIPART_OPERATIONS = Set.of("uploadFile");

    private static final Set<String> IDEMPOTENCY_OPERATIONS =
        Set.of(
            "issueCredential",
            "revokeCredential",
            "reissueCredential",
            "issueLearningBadge"
        );

    private static final Set<String> LIST_OPERATIONS =
        Set.of(
            "listInstitutions",
            "listMyInstructorApplications",
            "listInstitutionInstructorApplications",
            "listUsers",
            "listCourses",
            "listCourseSessions",
            "listCourseEnrollments",
            "listSessionAttendance",
            "listUserCredentials",
            "listCurrentUserCredentials",
            "listCredentialVerifications",
            "listUserBadges",
            "listMyEnrollments",
            "listInstructorCourses",
            "listInstitutionCourses",
            "listInstitutionEnrollments",
            "listInstitutionInstructors",
            "listNotices",
            "listAdminNotices",
            "listFaqs",
            "listMyInquiries",
            "listInstructorInquiries",
            "listInstitutionInquiries",
            "listMyReviews",
            "listCourseReviews",
            "listMyInterests",
            "listLearningCourses",
            "listInstructorEnrollmentStatus",
            "listInstitutionJoinApplications",
            "listBlockchainTransactions",
            "listBlockchainAlerts"
        );

    @Test
    void pathsMethodsOperationIdsAndSuccessStatusesExactlyMatchTheMatrix() {
        OpenAPI openAPI = parsedContract(false);

        assertThat(openAPI.getPaths()).hasSize(110);
        assertThat(openAPI.getPaths().keySet())
            .containsExactlyInAnyOrderElementsOf(
                EXPECTED.stream().map(ExpectedOperation::path).collect(Collectors.toSet())
            )
            .allMatch(path -> !path.startsWith("/api/v1"));

        Map<String, Set<PathItem.HttpMethod>> expectedMethods = new LinkedHashMap<>();
        EXPECTED.forEach(expected -> expectedMethods
            .computeIfAbsent(expected.path(), ignored -> new LinkedHashSet<>())
            .add(expected.method()));
        for (Map.Entry<String, Set<PathItem.HttpMethod>> expected :
            expectedMethods.entrySet()) {
            assertThat(openAPI.getPaths().get(expected.getKey()).readOperationsMap().keySet())
                .as(expected.getKey())
                .containsExactlyInAnyOrderElementsOf(expected.getValue());
        }

        assertThat(openAPI.getPaths().values().stream()
            .flatMap(pathItem -> pathItem.readOperations().stream())
            .toList()).hasSize(132);
        Set<String> operationIds = new LinkedHashSet<>();
        for (ExpectedOperation expected : EXPECTED) {
            Operation operation = operation(openAPI, expected);
            assertThat(operation.getOperationId()).isEqualTo(expected.operationId());
            assertThat(operation.getResponses())
                .as(expected.operationId())
                .containsKey(expected.successStatus());
            operationIds.add(operation.getOperationId());
        }
        assertThat(operationIds).hasSize(132);
        assertThat(operationIds)
            .containsExactlyInAnyOrderElementsOf(
                EXPECTED.stream()
                    .map(ExpectedOperation::operationId)
                    .collect(Collectors.toSet())
            );
    }

    @Test
    void requestBodiesExactlyMatchTheMatrixWithoutExtras() {
        OpenAPI openAPI = parsedContract(false);

        for (ExpectedOperation expected : EXPECTED) {
            Operation operation = operation(openAPI, expected);
            String expectedSchema = REQUEST_SCHEMAS.get(expected.operationId());
            if (expectedSchema == null) {
                assertThat(operation.getRequestBody())
                    .as(expected.operationId())
                    .isNull();
                continue;
            }
            assertThat(operation.getRequestBody())
                .as(expected.operationId())
                .isNotNull();
            assertThat(operation.getRequestBody().getRequired())
                .as("%s request body optionality", expected.operationId())
                .isEqualTo(!Set.of("issueCredential", "applyInstructor",
                        "adminResetPassword")
                    .contains(expected.operationId()));
            String mediaType = MULTIPART_OPERATIONS.contains(expected.operationId())
                ? "multipart/form-data" : "application/json";
            assertThat(operation.getRequestBody().getContent().keySet())
                .as(expected.operationId())
                .containsExactly(mediaType);
            assertThat(operation.getRequestBody().getContent()
                .get(mediaType).getSchema().get$ref())
                .endsWith("/" + expectedSchema);
        }
    }

    @Test
    void publicAndBearerOperationsMatchTheExplicitMatrix() {
        OpenAPI openAPI = parsedContract(false);

        assertThat(openAPI.getSecurity()).hasSize(1);
        assertBearer(openAPI.getSecurity());

        int publicCount = 0;
        int bearerCount = 0;
        for (ExpectedOperation expected : EXPECTED) {
            Operation operation = operation(openAPI, expected);
            if (PUBLIC_OPERATIONS.contains(expected.operationId())) {
                assertThat(operation.getSecurity())
                    .as(expected.operationId())
                    .isNotNull()
                    .isEmpty();
                publicCount++;
            } else {
                List<SecurityRequirement> effective =
                    operation.getSecurity() == null
                        ? openAPI.getSecurity()
                        : operation.getSecurity();
                assertBearer(effective);
                bearerCount++;
            }
        }
        assertThat(publicCount).isEqualTo(20);
        assertThat(bearerCount).isEqualTo(112);
    }

    @Test
    void idempotencyHeaderAppearsOnExactlyFourOperations() {
        OpenAPI openAPI = parsedContract(false);
        Set<String> actual = new LinkedHashSet<>();

        for (ExpectedOperation expected : EXPECTED) {
            List<Parameter> idempotencyParameters =
                effectiveParameters(openAPI, expected).stream()
                    .filter(OpenApiOperationCoverageTest::isIdempotencyKey)
                    .toList();
            boolean expectedIdempotency =
                IDEMPOTENCY_OPERATIONS.contains(expected.operationId());
            if (!idempotencyParameters.isEmpty()) {
                actual.add(expected.operationId());
            }
            assertThat(idempotencyParameters)
                .as(expected.operationId())
                .hasSize(expectedIdempotency ? 1 : 0);
            if (expectedIdempotency) {
                assertThat(idempotencyParameters.get(0).get$ref())
                    .as("%s reusable IdempotencyKey", expected.operationId())
                    .endsWith("/IdempotencyKey");
            }
        }

        assertThat(actual).containsExactlyInAnyOrderElementsOf(IDEMPOTENCY_OPERATIONS);
    }

    @Test
    void listOperationsUseExactPagingParametersAndPageEnvelopes() {
        OpenAPI raw = parsedContract(false);
        OpenAPI resolved = parsedContract(true);

        for (ExpectedOperation expected : EXPECTED) {
            Operation operation = operation(raw, expected);
            Set<String> paginationReferences = safeParameters(operation).stream()
                .map(Parameter::get$ref)
                .filter(reference -> reference != null)
                .map(reference -> reference.substring(reference.lastIndexOf('/') + 1))
                .filter(Set.of("Page", "Size", "Sort")::contains)
                .collect(Collectors.toSet());
            if (LIST_OPERATIONS.contains(expected.operationId())) {
                assertThat(paginationReferences)
                    .as(expected.operationId())
                    .containsExactlyInAnyOrder("Page", "Size", "Sort");
                Schema<?> responseSchema = operation(resolved, expected).getResponses()
                    .get(expected.successStatus())
                    .getContent().get("application/json").getSchema();
                assertThat(responseSchema.getProperties().keySet())
                    .as(expected.operationId())
                    .containsExactlyInAnyOrder("data", "page", "meta");
                Schema<?> pageSchema = (Schema<?>) responseSchema.getProperties().get("page");
                assertThat(pageSchema.getProperties().keySet())
                    .containsExactlyInAnyOrder(
                        "page", "size", "totalElements", "totalPages");
            } else {
                assertThat(paginationReferences)
                    .as(expected.operationId())
                    .isEmpty();
            }
        }
    }

    @Test
    void everySuccessResponseUsesTheRequiredEnvelopeShape() {
        OpenAPI openAPI = parsedContract(true);

        for (ExpectedOperation expected : EXPECTED) {
            if (RAW_SUCCESS_OPERATIONS.contains(expected.operationId())) {
                assertThat(operation(openAPI, expected).getResponses()
                    .get(expected.successStatus()).getContent()).isNotEmpty();
                continue;
            }
            Schema<?> responseSchema = operation(openAPI, expected).getResponses()
                .get(expected.successStatus())
                .getContent().get("application/json").getSchema();
            Set<String> expectedProperties = LIST_OPERATIONS.contains(expected.operationId())
                ? Set.of("data", "page", "meta")
                : Set.of("data", "meta");
            assertThat(responseSchema.getProperties().keySet())
                .as(expected.operationId())
                .containsExactlyInAnyOrderElementsOf(expectedProperties);
        }
    }

    private static ExpectedOperation op(
        PathItem.HttpMethod method,
        String path,
        String operationId,
        String successStatus
    ) {
        return new ExpectedOperation(method, path, operationId, successStatus);
    }

    private static OpenAPI parsedContract(boolean fullyResolved) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(fullyResolved);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(),
            null,
            options
        );
        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        return result.getOpenAPI();
    }

    private static Operation operation(
        OpenAPI openAPI,
        ExpectedOperation expected
    ) {
        return openAPI.getPaths().get(expected.path())
            .readOperationsMap().get(expected.method());
    }

    private static boolean isIdempotencyKey(Parameter parameter) {
        String reference = parameter.get$ref();
        return (reference != null && reference.endsWith("/IdempotencyKey"))
            || "Idempotency-Key".equals(parameter.getName());
    }

    private static List<Parameter> effectiveParameters(
        OpenAPI openAPI,
        ExpectedOperation expected
    ) {
        List<Parameter> parameters = new ArrayList<>();
        PathItem pathItem = openAPI.getPaths().get(expected.path());
        if (pathItem.getParameters() != null) {
            parameters.addAll(pathItem.getParameters());
        }
        parameters.addAll(safeParameters(operation(openAPI, expected)));
        return parameters;
    }

    private static List<Parameter> safeParameters(Operation operation) {
        return operation.getParameters() == null
            ? List.of()
            : new ArrayList<>(operation.getParameters());
    }

    private static void assertBearer(List<SecurityRequirement> security) {
        assertThat(security).hasSize(1);
        assertThat(security.get(0).keySet()).containsExactly("bearerAuth");
        assertThat(security.get(0).get("bearerAuth")).isEmpty();
    }
}
