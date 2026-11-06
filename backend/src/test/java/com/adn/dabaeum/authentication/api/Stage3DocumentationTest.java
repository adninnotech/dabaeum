package com.adn.dabaeum.authentication.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Stage3DocumentationTest {


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
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Schema> resolvedSchemas(OpenAPI openAPI) {
        return (Map<String, Schema>) (Map<?, ?>)
            openAPI.getComponents().getSchemas();
    }
}
