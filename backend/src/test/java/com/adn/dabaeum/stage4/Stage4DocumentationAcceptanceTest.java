package com.adn.dabaeum.stage4;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Stage4DocumentationAcceptanceTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final Set<String> STAGE4_OPERATION_IDS = Set.of(
        "listCourses", "createCourse", "getCourse", "updateCourse", "publishCourse", "closeCourse",
        "listCourseSessions", "createCourseSession", "getCourseSession", "updateCourseSession",
        "listCourseEnrollments", "createEnrollment", "createProxyEnrollment", "getEnrollment",
        "approveEnrollment", "rejectEnrollment", "cancelEnrollment", "withdrawEnrollment");
    private static final Set<String> CREATED_OPERATION_IDS = Set.of(
        "createCourse", "createCourseSession", "createEnrollment", "createProxyEnrollment");

    @Test
    void stage4AsciiDocIncludesAll18OperationSnippets() {
        assertThat(Files.exists(Path.of("src/docs/asciidoc/stage4.adoc"))).isTrue();
        String document = read("src/docs/asciidoc/stage4.adoc");
        for (String operation : List.of(
            "course-list", "course-create", "course-get", "course-update", "course-publish",
            "course-close", "course-session-list", "course-session-create", "course-session-get",
            "course-session-update", "enrollment-list", "enrollment-create", "enrollment-proxy-create",
            "enrollment-get", "enrollment-approve", "enrollment-reject", "enrollment-cancel",
            "enrollment-withdraw")) {
            assertThat(document).contains(operation);
        }
    }

    @Test
    void canonicalContractDescribesAllStage4OperationsAndSafeBodies() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(CONTRACT.toUri().toString(), null,
            options);
        assertThat(result.getMessages()).isEmpty();
        List<Operation> operations = new ArrayList<>();
        result.getOpenAPI().getPaths().values().forEach(path -> operations.addAll(path.readOperations()));
        assertThat(operations).filteredOn(operation ->
            STAGE4_OPERATION_IDS.contains(operation.getOperationId())).hasSize(18);
        assertThat(operations).filteredOn(operation ->
            STAGE4_OPERATION_IDS.contains(operation.getOperationId()))
            .allSatisfy(operation -> assertThat(operation.getResponses())
                .containsKey(CREATED_OPERATION_IDS.contains(operation.getOperationId()) ? "201" : "200"));
        Operation proxy = operations.stream()
            .filter(operation -> "createProxyEnrollment".equals(operation.getOperationId()))
            .findFirst().orElseThrow();
        assertThat(proxy.getRequestBody().getContent().get("application/json")
            .getSchema().getProperties()).containsOnlyKeys("userId");
        Operation approve = operations.stream()
            .filter(operation -> "approveEnrollment".equals(operation.getOperationId()))
            .findFirst().orElseThrow();
        assertThat(approve.getRequestBody()).isNull();
        Operation reject = operations.stream()
            .filter(operation -> "rejectEnrollment".equals(operation.getOperationId()))
            .findFirst().orElseThrow();
        assertThat(reject.getRequestBody()).isNotNull();
    }

    private String read(String file) {
        try {
            return Files.readString(Path.of(file));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
