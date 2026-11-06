package com.adn.dabaeum.credential.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CredentialSecurityAcceptanceTest {


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

}
