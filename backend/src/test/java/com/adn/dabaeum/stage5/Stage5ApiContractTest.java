package com.adn.dabaeum.stage5;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.ApiErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Stage5ApiContractTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    private static final Map<String, ExpectedOperation> OPERATIONS = Map.ofEntries(
        Map.entry("issueAttendanceQrToken", op(PathItem.HttpMethod.POST,
            "/sessions/{sessionId}/qr-token", "201")),
        Map.entry("listSessionAttendance", op(PathItem.HttpMethod.GET,
            "/sessions/{sessionId}/attendance", "200")),
        Map.entry("recordAttendance", op(PathItem.HttpMethod.POST,
            "/sessions/{sessionId}/attendance", "201")),
        Map.entry("getAttendance", op(PathItem.HttpMethod.GET,
            "/attendance/{attendanceId}", "200")),
        Map.entry("adjustAttendance", op(PathItem.HttpMethod.PATCH,
            "/attendance/{attendanceId}", "200")),
        Map.entry("getAttendanceSummary", op(PathItem.HttpMethod.GET,
            "/enrollments/{enrollmentId}/attendance-summary", "200")),
        Map.entry("getCompletion", op(PathItem.HttpMethod.GET,
            "/enrollments/{enrollmentId}/completion", "200")),
        Map.entry("evaluateCompletion", op(PathItem.HttpMethod.POST,
            "/enrollments/{enrollmentId}/completion/evaluate", "200")),
        Map.entry("confirmCompletion", op(PathItem.HttpMethod.POST,
            "/enrollments/{enrollmentId}/completion/confirm", "200"))
    );

    @Test
    void stage5OperationsPreservePathsOperationIdsAndSuccessStatuses() {
        OpenAPI openAPI = parsedContract();

        for (Map.Entry<String, ExpectedOperation> entry : OPERATIONS.entrySet()) {
            ExpectedOperation expected = entry.getValue();
            Operation operation = operation(openAPI, expected);
            assertThat(operation).as(entry.getKey()).isNotNull();
            assertThat(operation.getOperationId()).isEqualTo(entry.getKey());
            assertThat(operation.getResponses()).containsKey(expected.successStatus());
        }
    }

    @Test
    void attendanceCreateRequestDocumentsConditionalQrToken() {
        Schema<?> request = schema(parsedContract(), "AttendanceCreateRequest");
        assertThat(request.getProperties()).containsKey("qrToken");

        Schema<?> qrToken = (Schema<?>) request.getProperties().get("qrToken");
        assertThat(qrToken.getTypes()).containsExactlyInAnyOrder("string", "null");
        assertThat(qrToken.getMinLength()).isEqualTo(32);
        assertThat(qrToken.getMaxLength()).isEqualTo(2048);
        assertThat(qrToken.getDescription())
            .contains("attendanceMethod=QR")
            .contains("required");
        assertThat(request.getRequired()).doesNotContain("qrToken");
    }

    @Test
    void stage5ErrorsAndAccessRetryDescriptionsAreExplicit() {
        assertThat(Set.of(ApiErrorCode.ATTENDANCE_NOT_FOUND,
            ApiErrorCode.ATTENDANCE_CONFLICT,
            ApiErrorCode.ATTENDANCE_WINDOW_CLOSED,
            ApiErrorCode.ATTENDANCE_QR_INVALID,
            ApiErrorCode.ATTENDANCE_QR_EXPIRED,
            ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED,
            ApiErrorCode.COMPLETION_NOT_FOUND,
            ApiErrorCode.COMPLETION_STATUS_CONFLICT,
            ApiErrorCode.COMPLETION_METRICS_CONFLICT))
            .allMatch(code -> Set.of(ApiErrorCode.values()).contains(code));

        OpenAPI openAPI = parsedContract();
        for (Map.Entry<String, ExpectedOperation> entry : OPERATIONS.entrySet()) {
            Operation operation = operation(openAPI, entry.getValue());
            String description = operation.getDescription();
            assertThat(description).as(entry.getKey()).isNotBlank();
            assertThat(description).as(entry.getKey())
                .containsAnyOf("Access:", "호출할 수 있습니다", "호출합니다");
            assertThat(description).as(entry.getKey()).containsAnyOf("institution", "기관");
            assertThat(description).as(entry.getKey()).contains("Idempotency-Key");
            assertThat(parameters(operation))
                .as(entry.getKey())
                .noneMatch(Stage5ApiContractTest::isIdempotencyKey);
        }

        assertThat(operation(openAPI, OPERATIONS.get("issueAttendanceQrToken"))
            .getDescription()).contains("PLATFORM_ADMIN", "INSTITUTION_ADMIN", "MAIN_INSTRUCTOR");
        assertThat(operation(openAPI, OPERATIONS.get("recordAttendance"))
            .getDescription()).contains("ENROLLMENT_SUBJECT");
        assertThat(operation(openAPI, OPERATIONS.get("evaluateCompletion"))
            .getDescription()).contains("MAIN_INSTRUCTOR");
    }

    private static ExpectedOperation op(
        PathItem.HttpMethod method,
        String path,
        String successStatus
    ) {
        return new ExpectedOperation(method, path, successStatus);
    }

    private static Operation operation(OpenAPI openAPI, ExpectedOperation expected) {
        return openAPI.getPaths().get(expected.path()).readOperationsMap()
            .get(expected.method());
    }

    private static Schema<?> schema(OpenAPI openAPI, String name) {
        Schema<?> schema = openAPI.getComponents().getSchemas().get(name);
        assertThat(schema).as(name).isNotNull();
        return schema;
    }

    private static List<Parameter> parameters(Operation operation) {
        return operation.getParameters() == null
            ? List.of()
            : operation.getParameters();
    }

    private static boolean isIdempotencyKey(Parameter parameter) {
        String reference = parameter.get$ref();
        return (reference != null && reference.endsWith("/IdempotencyKey"))
            || "Idempotency-Key".equals(parameter.getName());
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

    private record ExpectedOperation(
        PathItem.HttpMethod method,
        String path,
        String successStatus
    ) {
    }
}
