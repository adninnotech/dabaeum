package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialApiContractTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    @Test
    void credentialOperationsHaveTheExactContractAndAuthorization() {
        OpenAPI api = parse();
        Map<String, ExpectedOperation> expected = Map.of(
            "/completions/{completionId}/credentials", op(PathItem.HttpMethod.POST, "issueCredential", "202"),
            "/credentials/{credentialId}", op(PathItem.HttpMethod.GET, "getCredential", "200"),
            "/credentials/{credentialId}/document", op(PathItem.HttpMethod.GET, "downloadCredentialDocument", "200"),
            "/users/{userId}/credentials", op(PathItem.HttpMethod.GET, "listUserCredentials", "200"),
            "/users/me/credentials", op(PathItem.HttpMethod.GET, "listCurrentUserCredentials", "200"),
            "/credentials/{credentialId}/revoke", op(PathItem.HttpMethod.POST, "revokeCredential", "202"),
            "/credentials/{credentialId}/reissue", op(PathItem.HttpMethod.POST, "reissueCredential", "202"),
            "/credentials/verify", op(PathItem.HttpMethod.POST, "verifyCredential", "200"),
            "/credentials/{credentialId}/verifications", op(PathItem.HttpMethod.GET, "listCredentialVerifications", "200")
        );

        assertThat(api.getPaths().keySet().stream()
            .filter(path -> path.contains("credential"))
            .filter(path -> !path.startsWith("/vc/"))
            .filter(path -> !path.contains("badges")))
            .containsExactlyInAnyOrderElementsOf(expected.keySet());

        expected.forEach((path, expectedOperation) -> {
            Map<PathItem.HttpMethod, Operation> operations = api.getPaths().get(path).readOperationsMap();
            assertThat(operations.keySet()).containsExactly(expectedOperation.method());
            Operation operation = operations.get(expectedOperation.method());
            assertThat(operation.getOperationId()).isEqualTo(expectedOperation.operationId());
            assertThat(operation.getTags()).containsExactly("Credential");
            assertThat(operation.getResponses()).containsKey(expectedOperation.successStatus());
            if (operation.getOperationId().equals("verifyCredential")) {
                assertThat(operation.getSecurity()).isEmpty();
            } else {
                assertThat(operation.getSecurity()).hasSize(1);
                assertThat(operation.getSecurity().getFirst()).containsOnlyKeys("bearerAuth");
            }
        });
    }

    @Test
    void vcPublicResourcesAreExplicitlyUnauthenticatedAndVersioned() {
        OpenAPI api = parse();
        Map<String, String> operations = Map.of(
            "/vc/contexts/lifelong-education/v1", "getLifelongEducationContextV1",
            "/vc/vocabulary/lifelong-education/v1", "getLifelongEducationVocabularyV1",
            "/vc/issuers/{institutionId}", "getCredentialIssuer",
            "/vc/status/{credentialNo}", "getPublicCredentialStatus",
            "/vc/status-lists/{listId}", "getCredentialStatusList");

        operations.forEach((path, operationId) -> {
            Operation operation = api.getPaths().get(path).getGet();
            assertThat(operation.getOperationId()).isEqualTo(operationId);
            assertThat(operation.getTags()).containsExactly("VC Public");
            assertThat(operation.getSecurity()).isEmpty();
            assertThat(operation.getResponses()).containsKey("200");
        });

        Operation download = api.getPaths()
            .get("/credentials/{credentialId}/document").getGet();
        assertThat(download.getResponses().get("200").getContent())
            .containsKey("application/vc+jwt");
        assertThat(api.getPaths().get("/vc/status/{credentialNo}").getGet().getResponses())
            .containsKey("503");
        Operation statusList = api.getPaths().get("/vc/status-lists/{listId}").getGet();
        assertThat(statusList.getResponses().get("200").getContent())
            .containsKey("application/vc+jwt");
        assertThat(statusList.getResponses()).containsKeys("404", "503");
    }

    private static ExpectedOperation op(PathItem.HttpMethod method, String operationId, String successStatus) {
        return new ExpectedOperation(method, operationId, successStatus);
    }

    private static OpenAPI parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(CONTRACT.toUri().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        return result.getOpenAPI();
    }

    private record ExpectedOperation(PathItem.HttpMethod method, String operationId, String successStatus) {
    }
}
