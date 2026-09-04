package com.adn.dabaeum.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FrontendApiDocumentationTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    private static final Set<String> STAGE4_OPERATION_IDS = Set.of(
        "listCourses",
        "createCourse",
        "getCourse",
        "updateCourse",
        "publishCourse",
        "closeCourse",
        "listCourseSessions",
        "createCourseSession",
        "getCourseSession",
        "updateCourseSession",
        "listCourseEnrollments",
        "createEnrollment",
        "createProxyEnrollment",
        "getEnrollment",
        "approveEnrollment",
        "rejectEnrollment",
        "cancelEnrollment",
        "withdrawEnrollment"
    );

    @Test
    void exposesAllStage4OperationsAndSafeEnrollmentRequests() {
        OpenAPI openAPI = parsedContract();
        Map<String, Operation> operations = operations(openAPI).stream()
            .collect(java.util.stream.Collectors.toMap(
                Operation::getOperationId,
                operation -> operation
            ));

        assertThat(operations.keySet())
            .containsAll(STAGE4_OPERATION_IDS);

        Schema<?> proxy = schema(openAPI, "ProxyEnrollmentCreateRequest");
        assertThat(proxy.getRequired()).containsExactly("userId");
        assertThat(proxy.getProperties()).containsOnlyKeys("userId");

        assertThat(operations.get("approveEnrollment").getRequestBody())
            .isNull();

        Operation reject = operations.get("rejectEnrollment");
        assertThat(reject.getRequestBody()).isNotNull();
        Schema<?> rejectionBody = reject.getRequestBody()
            .getContent()
            .get("application/json")
            .getSchema();
        assertThat(rejectionBody.get$ref())
            .endsWith("/EnrollmentRejectionRequest");

        Schema<?> rejection = schema(openAPI, "EnrollmentRejectionRequest");
        assertThat(rejection.getRequired()).containsExactly("reason");
        Schema<?> reason = (Schema<?>) rejection.getProperties().get("reason");
        assertThat(reason.getTypes()).containsExactly("string");
        assertThat(reason.getMinLength()).isEqualTo(1);
        assertThat(reason.getMaxLength()).isEqualTo(1000);
    }

    @Test
    void documentsPublicLocalSignupLoginAndGlobalLearnerMeaning() {
        OpenAPI openAPI = parsedContract();
        Map<String, Operation> operations = operations(openAPI).stream()
            .collect(java.util.stream.Collectors.toMap(
                Operation::getOperationId,
                operation -> operation
            ));

        Operation signup = operations.get("signupLocalAccount");
        Operation login = operations.get("loginLocalAccount");
        assertThat(signup).isNotNull();
        assertThat(login).isNotNull();
        assertThat(signup.getSecurity()).isEmpty();
        assertThat(login.getSecurity()).isEmpty();
        assertThat(signup.getSummary()).contains("회원가입");
        assertThat(signup.getDescription())
            .contains("기관")
            .contains("전역 LEARNER");
        assertThat(login.getDescription()).contains("Bearer");
        assertThat(openAPI.getComponents().getResponses()
            .get("LocalAccountCreated").getHeaders())
            .containsKey("Location");

        Schema<?> signupRequest = schema(openAPI, "SignupRequest");
        assertThat(signupRequest.getRequired())
            .containsExactlyInAnyOrder("email", "password", "name");
        assertThat(signupRequest.getProperties())
            .containsOnlyKeys("email", "password", "name", "phone", "birthDate")
            .doesNotContainKeys("institutionId", "role", "status");

        Schema<?> authToken = schema(openAPI, "AuthToken");
        Schema<?> accessToken = (Schema<?>) authToken.getProperties()
            .get("accessToken");
        assertThat(accessToken.getWriteOnly()).isNotEqualTo(Boolean.TRUE);

        Schema<?> sessionRole = schema(openAPI, "SessionRole");
        Schema<?> institutionId = (Schema<?>) sessionRole.getProperties()
            .get("institutionId");
        assertThat(institutionId.getDescription()).contains("전역 학습자");
    }

    @Test
    void documentsInstructorApplicationAndCourseAssignmentForFrontend() {
        OpenAPI openAPI = parsedContract();
        Map<String, Operation> operations = operations(openAPI).stream()
            .collect(java.util.stream.Collectors.toMap(
                Operation::getOperationId,
                operation -> operation
            ));

        assertThat(operations.keySet()).contains(
            "applyInstructor",
            "listMyInstructorApplications",
            "listInstitutionInstructorApplications",
            "approveInstructorApplication",
            "rejectInstructorApplication",
            "listCourseInstructors",
            "assignCourseInstructor",
            "updateCourseInstructor",
            "removeCourseInstructor"
        );
        assertThat(operations.get("applyInstructor").getDescription())
            .contains("전역 LEARNER")
            .contains("기관 강사");
        assertThat(operations.get("approveInstructorApplication").getDescription())
            .contains("INSTRUCTOR")
            .contains("기관 관리자");
        assertThat(operations.get("assignCourseInstructor").getDescription())
            .contains("같은 기관")
            .contains("MAIN")
            .contains("ASSISTANT");

        Schema<?> application = schema(openAPI, "InstructorApplication");
        assertThat(application.getRequired()).contains(
            "id", "userId", "institutionId", "status", "appliedAt"
        );
        Schema<?> reject = schema(
            openAPI,
            "InstructorApplicationRejectRequest"
        );
        assertThat(reject.getRequired()).containsExactly("rejectionReason");
        Schema<?> reason = (Schema<?>) reject.getProperties()
            .get("rejectionReason");
        assertThat(reason.getMinLength()).isEqualTo(1);
        assertThat(reason.getMaxLength()).isEqualTo(1000);

        Schema<?> assignment = schema(openAPI, "CourseInstructor");
        assertThat(assignment.getProperties()).doesNotContainKey(
            "instructorPhone"
        );
        Schema<?> role = (Schema<?>) assignment.getProperties().get("role");
        assertThat(role.getEnum().stream().map(String::valueOf).toList())
            .containsExactly("MAIN", "ASSISTANT");
    }

    @Test
    void enablesOnlyLocalSwaggerUiAndDisablesGeneratedPartialDocs() throws Exception {
        String common = Files.readString(
            Path.of("src/main/resources/application.yml"));
        String local = Files.readString(
            Path.of("src/main/resources/application-local.yml"));

        assertThat(common)
            .contains("springdoc:")
            .contains("api-docs:\n    enabled: false")
            .contains("swagger-ui:\n    enabled: false");
        assertThat(local)
            .contains("springdoc:")
            .contains("api-docs:\n    enabled: true")
            .contains("enable-default-api-docs: false")
            .contains("swagger-ui:\n    enabled: true")
            .contains("config-url: /openapi/swagger-config")
            .contains("url: /openapi/dabaeum-api-v1.yaml");
    }

    private static OpenAPI parsedContract() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(),
            null,
            options
        );

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        return result.getOpenAPI();
    }

    private static List<Operation> operations(OpenAPI openAPI) {
        List<Operation> operations = new ArrayList<>();
        openAPI.getPaths().values()
            .forEach(pathItem -> operations.addAll(pathItem.readOperations()));
        return operations;
    }

    private static Schema<?> schema(OpenAPI openAPI, String name) {
        Schema<?> schema = openAPI.getComponents().getSchemas().get(name);
        assertThat(schema).as(name).isNotNull();
        return schema;
    }
}
