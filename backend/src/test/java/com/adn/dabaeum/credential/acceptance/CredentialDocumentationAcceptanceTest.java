package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CredentialDocumentationAcceptanceTest {

    @Test
    void credentialAsciiDocIncludesEverySnippetInOrder() throws Exception {
        Path source = Path.of("src/docs/asciidoc/credential-vc-fabric.adoc");
        String document = Files.readString(source);
        List<String> snippets = List.of(
            "credential-issue", "credential-get", "credential-user-list", "credential-revoke",
            "credential-reissue", "credential-verify", "credential-verification-list");
        int previous = -1;
        for (String snippet : snippets) {
            int current = document.indexOf(snippet);
            assertThat(current).as("snippet %s", snippet).isGreaterThan(previous);
            previous = current;
        }
        assertThat(document).contains("향후 구현 예정", "202 polling", "공개 검증");
        assertThat(document).contains(
            "/api/v1/vc/contexts/lifelong-education/v1",
            "/api/v1/vc/vocabulary/lifelong-education/v1",
            "/api/v1/vc/issuers/{institutionId}",
            "/api/v1/vc/status/{credentialNo}",
            "/api/v1/vc/status-lists/{listId}",
            "BitstringStatusListEntry",
            "/api/v1/credentials/{credentialId}/document",
            "application/vc+jwt",
            "Cache-Control: no-store");
    }
}
