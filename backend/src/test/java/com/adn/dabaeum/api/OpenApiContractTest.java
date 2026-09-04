package com.adn.dabaeum.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiContractTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    private static final Set<String> BUSINESS_SCHEMAS = Set.of(
        "Institution",
        "InstitutionCreateRequest",
        "InstitutionUpdateRequest",
        "InstructorApplication",
        "InstructorApplicationRequest",
        "InstructorApplicationRejectRequest",
        "User",
        "UserCreateRequest",
        "UserUpdateRequest",
        "UserStatusChangeRequest",
        "UserMeUpdateRequest",
        "Identity",
        "IdentityResponse",
        "IdentityLinkRequest",
        "Role",
        "RoleResponse",
        "RoleAssignmentRequest",
        "SessionRole",
        "Session",
        "SessionResponse",
        "Course",
        "CourseCreateRequest",
        "CourseUpdateRequest",
        "CourseInstructor",
        "CourseInstructorAssignRequest",
        "CourseInstructorUpdateRequest",
        "CourseSession",
        "CourseSessionCreateRequest",
        "CourseSessionUpdateRequest",
        "Enrollment",
        "EnrollmentCreateRequest",
        "ProxyEnrollmentCreateRequest",
        "EnrollmentRejectionRequest",
        "Attendance",
        "AttendanceCreateRequest",
        "AttendanceAdjustmentRequest",
        "AttendanceSummary",
        "Completion",
        "CompletionEvaluationRequest",
        "Credential",
        "CredentialIssueRequest",
        "CredentialRevokeRequest",
        "CredentialReissueRequest",
        "CredentialVerifyRequest",
        "CredentialVerification",
        "LearningBadge",
        "BadgeIssueRequest"
    );

    private static final Set<String> FORBIDDEN_PROPERTIES = Set.of(
        "deletedAt",
        "retryCount",
        "nextRetryAt",
        "secretRef",
        "failureCode",
        "failureMessage",
        "vcPayload",
        "rawVcPayload",
        "didPrivateKey",
        "apiKey",
        "accessToken",
        "clientSecret",
        "outboxPayload",
        "blockchainRequest",
        "blockchainResponse"
    );

    private static final Set<String> FRONTEND_TAGS = Set.of(
        "System",
        "Institution",
        "Auth",
        "User",
        "Identity & Role",
        "Instructor",
        "Course",
        "Course Session",
        "Enrollment",
        "Attendance",
        "Completion",
        "Credential",
        "VC Public",
        "Badge",
        "Support",
        "Inquiry",
        "Review",
        "Interest",
        "Notification",
        "File"
    );

    @Test
    void contractHasNoParserErrors() {
        SwaggerParseResult result = parse(true);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        assertThat(result.getOpenAPI().getOpenapi()).startsWith("3.1");
    }

    @Test
    void contractUsesTheSingleVersionedServerAndJsonResponses() {
        OpenAPI openAPI = parsedContract();

        assertThat(openAPI.getServers())
            .extracting(server -> server.getUrl())
            .containsExactly("/api/v1");

        for (Operation operation : operations(openAPI)) {
            for (Map.Entry<String, ApiResponse> response :
                operation.getResponses().entrySet()) {
                assertThat(response.getValue().getContent())
                    .as("%s response for %s", response.getKey(), operation.getOperationId())
                    .isNotNull();
                String expectedMediaType = switch (operation.getOperationId()) {
                    case "getLifelongEducationContextV1", "getCredentialIssuer" ->
                        response.getKey().equals("200")
                            ? "application/ld+json" : "application/json";
                    case "downloadCredentialDocument", "getCredentialStatusList" ->
                        response.getKey().equals("200")
                            ? "application/vc+jwt" : "application/json";
                    case "downloadFileContent" -> response.getKey().equals("200")
                        ? "application/octet-stream" : "application/json";
                    default -> "application/json";
                };
                assertThat(response.getValue().getContent().keySet())
                    .as("%s response media types for %s",
                        response.getKey(), operation.getOperationId())
                    .containsExactly(expectedMediaType);
            }
        }
    }

    @Test
    void everyOperationHasAFrontendTagSummaryAndDescription() {
        OpenAPI openAPI = parsedContract(false);

        assertThat(openAPI.getTags())
            .extracting(tag -> tag.getName())
            .containsExactlyInAnyOrderElementsOf(FRONTEND_TAGS);

        for (Operation operation : operations(openAPI)) {
            assertThat(operation.getTags())
                .as("tags for %s", operation.getOperationId())
                .isNotEmpty()
                .allMatch(FRONTEND_TAGS::contains);
            assertThat(operation.getSummary())
                .as("summary for %s", operation.getOperationId())
                .isNotBlank();
            assertThat(operation.getDescription())
                .as("description for %s", operation.getOperationId())
                .isNotBlank();
        }
    }

    @Test
    void commonComponentsHaveExactSecurityPaginationErrorAndIdempotencyContracts() {
        OpenAPI openAPI = parsedContract(false);

        SecurityScheme bearerAuth =
            openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(bearerAuth.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerAuth.getScheme()).isEqualTo("bearer");
        assertThat(bearerAuth.getBearerFormat()).isEqualTo("JWT");

        assertSchema(openAPI, "ApiMeta",
            List.of("requestId", "timestamp"),
            Map.of("requestId", "uuid", "timestamp", "date-time"));
        assertSchema(openAPI, "ApiError",
            List.of("code", "message", "details", "requestId", "timestamp"),
            Map.of("requestId", "uuid", "timestamp", "date-time"));
        assertType(property(openAPI, "ApiError", "details"), "array");
        assertType(property(openAPI, "ApiError", "details").getItems(), "string");
        assertThat(property(openAPI, "ApiError", "details").getTypes())
            .as("ApiError.details must be non-null")
            .doesNotContain("null");

        assertSchema(openAPI, "PageMeta",
            List.of("page", "size", "totalElements", "totalPages"),
            Map.of());
        assertIntegerProperty(openAPI, "PageMeta", "page", "int32", 0, null);
        assertIntegerProperty(openAPI, "PageMeta", "size", "int32", 1, 100);
        assertIntegerProperty(openAPI, "PageMeta", "totalElements", "int64", 0, null);
        assertIntegerProperty(openAPI, "PageMeta", "totalPages", "int32", 0, null);

        Parameter idempotencyKey =
            openAPI.getComponents().getParameters().get("IdempotencyKey");
        assertThat(idempotencyKey.getName()).isEqualTo("Idempotency-Key");
        assertThat(idempotencyKey.getIn()).isEqualTo("header");
        assertThat(idempotencyKey.getRequired()).isTrue();
        assertType(idempotencyKey.getSchema(), "string");
        assertThat(idempotencyKey.getSchema().getMinLength()).isEqualTo(8);
        assertThat(idempotencyKey.getSchema().getMaxLength()).isEqualTo(128);

        Map<String, ApiResponse> responses = openAPI.getComponents().getResponses();
        assertThat(responses.keySet()).contains(
            "BadRequest",
            "Unauthorized",
            "Forbidden",
            "NotFound",
            "Conflict",
            "UnprocessableEntity",
            "InternalServerError"
        );
        for (String name : List.of(
            "BadRequest",
            "Unauthorized",
            "Forbidden",
            "NotFound",
            "Conflict",
            "UnprocessableEntity",
            "InternalServerError"
        )) {
            Schema<?> schema = responses.get(name)
                .getContent().get("application/json").getSchema();
            assertThat(schema.get$ref())
                .as("%s must reference ApiError", name)
                .endsWith("/ApiError");
        }
        for (Operation operation : operations(openAPI)) {
            assertThat(operation.getResponses())
                .as("500 response for %s", operation.getOperationId())
                .containsKey("500");
        }
    }

    @Test
    void businessSchemasUseExactEnumsAndSafePublicProperties() {
        OpenAPI openAPI = parsedContract(false);
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();

        assertThat(schemas.keySet()).containsAll(BUSINESS_SCHEMAS);
        assertEnum(openAPI, "Institution", "status",
            "ACTIVE", "INACTIVE", "SUSPENDED");
        assertEnum(openAPI, "User", "status",
            "ACTIVE", "DORMANT", "WITHDRAWN", "SUSPENDED");
        assertEnum(openAPI, "Course", "educationType",
            "OFFLINE", "ONLINE", "HYBRID");
        assertEnum(openAPI, "Course", "status",
            "DRAFT", "RECRUITING", "RECRUITMENT_CLOSED",
            "IN_PROGRESS", "COMPLETED", "CANCELLED");
        assertEnum(openAPI, "CourseSession", "status",
            "SCHEDULED", "OPEN", "COMPLETED", "CANCELLED");
        assertEnum(openAPI, "Enrollment", "applicationType",
            "SELF", "ADMIN_PROXY", "EXTERNAL_SYNC");
        assertEnum(openAPI, "EnrollmentCreateRequest", "applicationType",
            "SELF", "ADMIN_PROXY", "EXTERNAL_SYNC");
        assertEnum(openAPI, "Enrollment", "status",
            "APPLIED", "WAITLISTED", "APPROVED",
            "REJECTED", "CANCELLED", "WITHDRAWN");
        assertEnum(openAPI, "Attendance", "attendanceMethod",
            "QR", "ADMIN", "EXTERNAL");
        assertEnum(openAPI, "Attendance", "status",
            "PRESENT", "LATE", "ABSENT", "EXCUSED");
        assertEnum(openAPI, "Attendance", "source",
            "APP", "ADMIN_WEB", "EXTERNAL_API");
        assertEnum(openAPI, "Completion", "status",
            "PENDING_EVALUATION", "ELIGIBLE", "COMPLETED",
            "NOT_COMPLETED", "CANCELLED");
        assertEnum(openAPI, "Credential", "status",
            "PENDING", "ISSUING", "ISSUED", "FAILED",
            "REVOKED", "SUPERSEDED", "EXPIRED");
        assertEnum(openAPI, "CredentialVerification", "verificationType",
            "QR", "API", "ADMIN");
        assertEnum(openAPI, "CredentialVerification", "requesterType",
            "INDIVIDUAL", "INSTITUTION", "EXTERNAL_ORGANIZATION", "SYSTEM");
        assertEnum(openAPI, "CredentialVerification", "result",
            "VALID", "INVALID", "REVOKED", "SUPERSEDED",
            "EXPIRED", "NOT_FOUND", "ERROR");
        assertEnum(openAPI, "LearningBadge", "status",
            "PENDING", "ISSUED", "FAILED", "REVOKED");

        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            Schema<?> schema = entry.getValue();
            if (schema.getProperties() == null) {
                continue;
            }
            Object additionalProperties = schema.getAdditionalProperties();
            boolean closed = Boolean.FALSE.equals(additionalProperties)
                || (additionalProperties instanceof Schema<?> booleanSchema
                    && Boolean.FALSE.equals(booleanSchema.getBooleanSchemaValue()));
            assertThat(closed)
                .as("%s additionalProperties", entry.getKey())
                .isTrue();
            assertThat(schema.getProperties().keySet())
                .as("%s lower camel case properties", entry.getKey())
                .allMatch(name -> name.equals("@context")
                    || name.matches("[a-z][A-Za-z0-9]*"));
            java.util.Set<String> publicProperties = new java.util.LinkedHashSet<>(
                schema.getProperties().keySet()
            );
            if (entry.getKey().equals("AuthToken")) {
                Schema<?> accessToken = (Schema<?>) schema.getProperties()
                    .get("accessToken");
                assertThat(accessToken.getWriteOnly()).isNotEqualTo(Boolean.TRUE);
                publicProperties.remove("accessToken");
            }
            assertThat(publicProperties)
                .as("%s forbidden public properties", entry.getKey())
                .doesNotContainAnyElementsOf(FORBIDDEN_PROPERTIES);
            for (Object value : schema.getProperties().values()) {
                Schema<?> schemaProperty = (Schema<?>) value;
                assertThat(schemaProperty.getNullable())
                    .as("%s must use OpenAPI 3.1 type arrays", entry.getKey())
                    .isNotEqualTo(Boolean.TRUE);
            }
        }

        assertThat(property(openAPI, "Institution", "businessNumber").getTypes())
            .containsExactlyInAnyOrder("string", "null");
        assertThat(property(openAPI, "Course", "description").getTypes())
            .containsExactlyInAnyOrder("string", "null");
        assertThat(property(openAPI, "Credential", "validUntil").getTypes())
            .containsExactlyInAnyOrder("string", "null");
        assertThat(openAPI.getComponents().getSchemas().get("Credential").getProperties().keySet())
            .contains("issuerIdentifier", "subjectIdentifier")
            .doesNotContain("issuerDid", "subjectDid", "vcPayload", "proof");
        assertThat(openAPI.getComponents().getSchemas().get("CredentialIssueRequest").getProperties())
            .containsOnlyKeys("validUntil");
    }

    @Test
    void identityPublicResponseExcludesRawProviderAndMetadataFields() {
        OpenAPI openAPI = parsedContract(false);
        Schema<?> identity = openAPI.getComponents()
            .getSchemas()
            .get("Identity");

        assertThat(identity.getProperties().keySet())
            .containsExactly("id", "provider", "verifiedAt", "createdAt", "updatedAt");
        assertThat(identity.getProperties().keySet())
            .doesNotContain("providerSubject", "externalDid", "metadata");
    }

    @Test
    void credentialVerificationRequestRequiresANonNullIdentifier() {
        OpenAPI openAPI = parsedContract(false);
        Schema<?> request = openAPI.getComponents().getSchemas()
            .get("CredentialVerifyRequest");

        assertThat(request.getAnyOf()).hasSize(2);
        assertThat(request.getAnyOf())
            .extracting(schema -> ((Schema<?>) schema).getRequired())
            .containsExactlyInAnyOrder(
                List.of("credentialNo"),
                List.of("credentialHash")
            );
        Schema<?> credentialNo = property(
            openAPI, "CredentialVerifyRequest", "credentialNo");
        Schema<?> credentialHash = property(
            openAPI, "CredentialVerifyRequest", "credentialHash");
        assertThat(credentialNo.getTypes()).containsExactly("string");
        assertThat(credentialNo.getMinLength()).isEqualTo(1);
        assertThat(credentialHash.getTypes()).containsExactly("string");
    }

    @Test
    void identifiersDatesAndUpdateRequestsUseTheRequiredShapes() {
        OpenAPI openAPI = parsedContract(false);

        for (Map.Entry<String, Schema> schemaEntry :
            openAPI.getComponents().getSchemas().entrySet()) {
            if (schemaEntry.getValue().getProperties() == null) {
                continue;
            }
            for (Object object : schemaEntry.getValue().getProperties().entrySet()) {
                Map.Entry<?, ?> propertyEntry = (Map.Entry<?, ?>) object;
                String name = (String) propertyEntry.getKey();
                Schema<?> schema = (Schema<?>) propertyEntry.getValue();
                if ((name.equals("id") || name.endsWith("Id"))
                    && !Set.of("requesterId", "nftTokenId").contains(name)) {
                    if (Set.of("VcVocabularyTerm", "VcVocabularyDocument",
                        "CredentialIssuerDocument", "CredentialVerificationMethod")
                        .contains(schemaEntry.getKey())) {
                        assertThat(schema.getFormat())
                            .as("%s.%s URI format", schemaEntry.getKey(), name)
                            .isEqualTo("uri");
                    } else {
                        assertThat(schema.getFormat())
                            .as("%s.%s UUID format", schemaEntry.getKey(), name)
                            .isEqualTo("uuid");
                    }
                }
                if (name.endsWith("At") || name.equals("timestamp")) {
                    assertThat(schema.getFormat())
                        .as("%s.%s timestamp format", schemaEntry.getKey(), name)
                        .isEqualTo("date-time");
                }
                if (name.endsWith("Date")) {
                    assertThat(schema.getFormat())
                        .as("%s.%s date format", schemaEntry.getKey(), name)
                        .isEqualTo("date");
                }
            }
        }

        for (String update : List.of(
            "InstitutionUpdateRequest",
            "CourseUpdateRequest",
            "CourseSessionUpdateRequest"
        )) {
            assertThat(openAPI.getComponents().getSchemas().get(update).getMinProperties())
                .as(update)
                .isEqualTo(1);
        }

        for (String create : List.of(
            "InstitutionCreateRequest",
            "CourseCreateRequest",
            "CourseSessionCreateRequest",
            "EnrollmentCreateRequest",
            "ProxyEnrollmentCreateRequest",
            "AttendanceCreateRequest"
        )) {
            assertThat(openAPI.getComponents().getSchemas().get(create).getProperties())
                .as("%s server-managed properties", create)
                .doesNotContainKeys("id", "createdAt", "updatedAt", "deletedAt");
        }

        assertEnum(openAPI, "SystemPingData", "status", "OK");
    }

    private static SwaggerParseResult parse(boolean resolveFully) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(resolveFully);
        return new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(),
            null,
            options
        );
    }

    private static OpenAPI parsedContract() {
        return parsedContract(true);
    }

    private static OpenAPI parsedContract(boolean fullyResolved) {
        SwaggerParseResult result = parse(fullyResolved);
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

    private static Schema<?> property(
        OpenAPI openAPI,
        String schemaName,
        String propertyName
    ) {
        return (Schema<?>) openAPI.getComponents().getSchemas()
            .get(schemaName).getProperties().get(propertyName);
    }

    private static void assertSchema(
        OpenAPI openAPI,
        String schemaName,
        List<String> required,
        Map<String, String> formats
    ) {
        Schema<?> schema = openAPI.getComponents().getSchemas().get(schemaName);
        assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(required);
        for (Map.Entry<String, String> format : formats.entrySet()) {
            assertThat(((Schema<?>) schema.getProperties().get(format.getKey())).getFormat())
                .as("%s.%s format", schemaName, format.getKey())
                .isEqualTo(format.getValue());
        }
    }

    private static void assertIntegerProperty(
        OpenAPI openAPI,
        String schemaName,
        String propertyName,
        String format,
        Integer minimum,
        Integer maximum
    ) {
        Schema<?> schema = property(openAPI, schemaName, propertyName);
        assertType(schema, "integer");
        assertThat(schema.getFormat()).isEqualTo(format);
        assertThat(schema.getMinimum().intValue()).isEqualTo(minimum);
        if (maximum != null) {
            assertThat(schema.getMaximum().intValue()).isEqualTo(maximum);
        }
    }

    private static void assertType(Schema<?> schema, String expected) {
        assertThat(schema.getTypes()).contains(expected);
    }

    private static void assertEnum(
        OpenAPI openAPI,
        String schemaName,
        String propertyName,
        String... values
    ) {
        assertThat(property(openAPI, schemaName, propertyName).getEnum())
            .extracting(Object::toString)
            .as("%s.%s enum", schemaName, propertyName)
            .containsExactly(values);
    }
}
