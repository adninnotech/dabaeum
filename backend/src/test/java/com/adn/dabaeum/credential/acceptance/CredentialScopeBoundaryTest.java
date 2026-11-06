package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.ApiErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CredentialScopeBoundaryTest {

    private static final Pattern BADGE_ROUTE_ANNOTATION = Pattern.compile(
        "@(?:RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)"
            + "\\s*\\([^)]*\\\"[^\\\"]*badges[^\\\"]*\\\"[^)]*\\)",
        Pattern.DOTALL
    );

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

}
