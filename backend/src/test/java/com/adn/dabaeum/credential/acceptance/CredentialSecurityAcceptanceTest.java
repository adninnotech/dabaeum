package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialSecurityAcceptanceTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    @Test
    void onlyPublicVerifyIsUnauthenticated() {
        OpenAPI api = parse();
        Map<String, Operation> operations = api.getPaths().values().stream()
            .flatMap(path -> path.readOperations().stream())
            .filter(operation -> operation.getTags() != null
                && operation.getTags().contains("Credential"))
            .collect(java.util.stream.Collectors.toMap(Operation::getOperationId, value -> value));

        assertThat(operations.get("verifyCredential").getSecurity()).isEmpty();
        operations.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("verifyCredential"))
            .forEach(entry -> assertThat(entry.getValue().getSecurity())
                .as(entry.getKey()).isNotEmpty());
    }

    /**
     * 백엔드는 원장에 REST(대구체인 계약)로만 붙는다 (ADR-0005). Fabric SDK·인증 자료·터널은
     * 에뮬레이터 모듈에 있으며, 백엔드 설정과 빌드에 다시 들어오면 안 된다.
     */
    @Test
    void ledgerAccessGoesThroughTheDaeguchainContractAndFabricRuntimeIsGone() throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        String build = Files.readString(Path.of("build.gradle"));
        String versions = Files.readString(Path.of("gradle/libs.versions.toml"));

        assertThat(common)
            .contains("provider: fake")
            .contains("write-enabled: ${DABAEUM_FABRIC_WRITE_APPROVED:false}")
            .contains("base-url: ${DABAEUM_DAEGUCHAIN_BASE_URL:")
            .doesNotContain("DABAEUM_FABRIC_ENABLED", "DABAEUM_FABRIC_CERTIFICATE_PATH",
                "DABAEUM_FABRIC_PRIVATE_KEY_PATH", "DABAEUM_FABRIC_TLS_CA_PATH", "ssh-password:");
        assertThat(build).doesNotContain("fabricWriteTest", "fabric.gateway", "grpc", "protobuf");
        assertThat(versions).doesNotContain("hyperledger", "grpc", "protobuf");
        assertThat(Path.of("src/main/java/com/adn/dabaeum/fabric/gateway")).doesNotExist();
        assertThat(Path.of("src/main/java/com/adn/dabaeum/fabric/provider")).doesNotExist();
        assertThat(Path.of("src/main/java/com/adn/dabaeum/fabric/ssh")).doesNotExist();
        assertThat(Path.of("src/fabricWriteTest")).doesNotExist();
        assertThat(Path.of("src/main/java/com/adn/dabaeum/daeguchain/provider/DaeguChainStorageProvider.java"))
            .isRegularFile();
        // Provider 중립 워커·트랜잭션 도메인은 남는다.
        assertThat(Path.of("src/main/java/com/adn/dabaeum/fabric/application")).isDirectory();
    }

    private static OpenAPI parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        return result.getOpenAPI();
    }
}
