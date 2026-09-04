package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.ApiErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CredentialScopeBoundaryTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final Pattern BADGE_ROUTE_ANNOTATION = Pattern.compile(
        "@(?:RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)"
            + "\\s*\\([^)]*\\\"[^\\\"]*badges[^\\\"]*\\\"[^)]*\\)",
        Pattern.DOTALL
    );

    @Test
    void credentialContractKeepsServerOwnedIdentifiersAndMinimalIssueInput() {
        OpenAPI api = parse();
        Schema<?> issueRequest = schema(api, "CredentialIssueRequest");
        Schema<?> credential = schema(api, "Credential");

        assertThat(issueRequest.getRequired()).isNullOrEmpty();
        assertThat(issueRequest.getProperties()).containsOnlyKeys("validUntil");
        assertThat(credential.getProperties()).containsKeys("issuerIdentifier", "subjectIdentifier");
        assertThat(credential.getProperties()).doesNotContainKeys(
            "issuerDid", "subjectDid", "did", "wallet", "nft", "daeguChain", "vcPayload", "proof");
    }

    @Test
    void badgeEndpointsAreDocumentedAsImplementedWithMvcHandlers() throws Exception {
        OpenAPI api = parse();
        List<String> badgeOperationIds = List.of("issueLearningBadge", "listUserBadges", "getLearningBadge");

        List<Operation> badgeOperations = api.getPaths().values().stream()
            .flatMap(pathItem -> pathItem.readOperations().stream())
            .filter(operation -> badgeOperationIds.contains(operation.getOperationId()))
            .toList();
        assertThat(badgeOperations).hasSize(3);
        assertThat(badgeOperations)
            .extracting(Operation::getDescription)
            .allSatisfy(description ->
                assertThat(description).doesNotContain("향후 구현 예정", "현재 미구현"));

        try (Stream<Path> sourceFiles = Files.walk(Path.of("src/main/java"))) {
            String source = sourceFiles
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> read(path))
                .reduce("", String::concat);
            assertThat(BADGE_ROUTE_ANNOTATION.matcher(source).find())
                .as("Spring MVC Badge route annotation")
                .isTrue();
        }
    }

    @Test
    void apiErrorCodeContainsExactlyTheRequiredCredentialAndFabricEntries() {
        Set<String> credentialAndFabricErrorCodes = Set.of(ApiErrorCode.values()).stream()
            .map(Enum::name)
            .filter(name -> name.equals("COMPLETION_NOT_CONFIRMED")
                || name.startsWith("CREDENTIAL_")
                || name.startsWith("FABRIC_"))
            .collect(java.util.stream.Collectors.toSet());

        assertThat(credentialAndFabricErrorCodes).containsExactlyInAnyOrder(
            "COMPLETION_NOT_CONFIRMED",
            "CREDENTIAL_NOT_FOUND",
            "CREDENTIAL_ALREADY_EXISTS",
            "CREDENTIAL_STATE_CONFLICT",
            "CREDENTIAL_IDEMPOTENCY_CONFLICT",
            "CREDENTIAL_PROOF_GENERATION_FAILED",
            "CREDENTIAL_PROOF_INVALID",
            "CREDENTIAL_HASH_MISMATCH",
            "CREDENTIAL_VERIFICATION_FAILED",
            "FABRIC_SUBMIT_FAILED",
            "FABRIC_COMMIT_TIMEOUT",
            "FABRIC_COMMIT_INVALID",
            "FABRIC_LEDGER_CONFLICT"
        );
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Schema<?> schema(OpenAPI api, String name) {
        return (Schema<?>) api.getComponents().getSchemas().get(name);
    }

    private static OpenAPI parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(CONTRACT.toUri().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        return result.getOpenAPI();
    }
}
