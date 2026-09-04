package com.adn.dabaeum.authentication.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Stage3DocumentationTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();

    private static final Set<String> FRONTEND_USER_OPERATIONS = Set.of(
        "listUsers",
        "createUser",
        "getCurrentUser",
        "updateCurrentUser",
        "getUser",
        "updateUser",
        "changeUserStatus"
    );

    private static final Set<String> SECRET_FIELDS = Set.of(
        "token",
        "accessToken",
        "providerSubject",
        "metadata",
        "secretRef",
        "apiKey",
        "clientSecret",
        "didPrivateKey"
    );

    @Test
    void stage3AcceptanceArtifactsExist() {
        assertThat(Files.exists(Path.of(
            "src/docs/asciidoc/stage3.adoc"
        ))).isTrue();
        assertThat(Files.exists(Path.of(
            "scripts/verify-stage3-identity-role-auth.sh"
        ))).isTrue();
    }

    @Test
    void frontendContractKeepsSevenUserOperationsAndAddsPublicSessionOnly() {
        OpenAPI openAPI = parsedContract();
        Map<String, Operation> operations = openAPI.getPaths().entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().readOperationsMap().entrySet()
                .stream()
                .map(operation -> Map.entry(
                    operation.getValue().getOperationId(),
                    operation.getValue()
                )))
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));

        assertThat(operations.keySet())
            .containsAll(FRONTEND_USER_OPERATIONS)
            .doesNotContainNull();
        assertThat(openAPI.getPaths()).containsKey("/auth/session");
        Operation session = openAPI.getPaths().get("/auth/session")
            .readOperationsMap().get(PathItem.HttpMethod.GET);
        assertThat(session.getOperationId()).isEqualTo("getAuthSession");
        assertThat(session.getResponses()).containsKey("401");

        Schema<?> sessionResponse = resolvedSchemas(openAPI).get(
            "SessionResponse"
        );
        assertThat(sessionResponse.getProperties().keySet())
            .containsExactlyInAnyOrder("data", "meta");
        Schema<?> sessionData = resolvedSchemas(openAPI).get("Session");
        assertThat(sessionData.getProperties().keySet())
            .containsExactlyInAnyOrder(
                "userId", "provider", "roles", "expiresAt"
            );
        assertThat(sessionData.getProperties().keySet())
            .doesNotContainAnyElementsOf(SECRET_FIELDS);
    }

    @Test
    void identityAndRoleSchemasExposeNoSecretBearingFields() {
        Map<String, Schema> schemas = resolvedSchemas(parsedContract());
        for (String schemaName : Set.of(
            "Identity",
            "IdentityResponse",
            "Role",
            "RoleResponse",
            "RoleListResponse"
        )) {
            Schema<?> schema = schemas.get(schemaName);
            assertThat(schema).as(schemaName).isNotNull();
            assertThat(schema.getProperties().keySet())
                .as(schemaName)
                .doesNotContainAnyElementsOf(SECRET_FIELDS);
        }
    }

    private static OpenAPI parsedContract() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(),
            null,
            options
        );
        assertThat(result.getMessages()).isEmpty();
        return result.getOpenAPI();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Schema> resolvedSchemas(OpenAPI openAPI) {
        return (Map<String, Schema>) (Map<?, ?>)
            openAPI.getComponents().getSchemas();
    }
}
