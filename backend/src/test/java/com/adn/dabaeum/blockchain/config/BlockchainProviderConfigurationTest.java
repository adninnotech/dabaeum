package com.adn.dabaeum.blockchain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.blockchain.provider.FakeBlockchainRegistryPort;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BlockchainProviderConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(BlockchainProviderConfiguration.class);

    @Test
    void properties_default_to_fake_provider_and_disabled_writes() {
        BlockchainProperties properties = new BlockchainProperties(null, false);

        assertThat(properties.provider()).isEqualTo("fake");
        assertThat(properties.writeEnabled()).isFalse();
        assertThat(properties.target()).isEqualTo(RegistryTarget.FABRIC_POC);
    }

    @Test
    void only_fake_and_daeguchain_providers_are_accepted() {
        assertThat(new BlockchainProperties("daeguchain", false).target())
            .isEqualTo(RegistryTarget.DAEGUCHAIN);
        // Fabric SDK 연동은 제거됐다. 옛 값이 설정에 남아 있으면 조용히 fake 로 떨어지지 않고 기동이 실패한다.
        assertThatThrownBy(() -> new BlockchainProperties("fabric-poc", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockchainProperties("other", false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void default_provider_selects_fake_registry_and_publishes_the_target() {
        context.run(result -> {
            assertThat(result).hasSingleBean(BlockchainRegistryPort.class);
            assertThat(result.getBean(BlockchainRegistryPort.class))
                .isInstanceOf(FakeBlockchainRegistryPort.class);
            assertThat(result.getBean(RegistryTarget.class)).isEqualTo(RegistryTarget.FABRIC_POC);
        });
    }

    @Test
    void daeguchain_provider_leaves_registry_assembly_to_its_own_configuration() {
        context.withPropertyValues("dabaeum.blockchain.provider=daeguchain")
            .run(result -> {
                assertThat(result).doesNotHaveBean(BlockchainRegistryPort.class);
                assertThat(result.getBean(RegistryTarget.class)).isEqualTo(RegistryTarget.DAEGUCHAIN);
            });
    }

    @Test
    void application_files_keep_provider_and_write_approval_without_fabric_connection_settings()
        throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));
        String dev = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(common)
            .contains("blockchain:")
            .contains("provider: fake")
            .contains("write-enabled: ${DABAEUM_FABRIC_WRITE_APPROVED:false}")
            .contains("daeguchain:")
            .contains("base-url: ${DABAEUM_DAEGUCHAIN_BASE_URL:http://127.0.0.1:8090}")
            .contains("project-id: ${DABAEUM_DAEGUCHAIN_PROJECT_ID:}");
        // Fabric SDK 접속 설정(채널·체인코드·인증 자료 경로)은 에뮬레이터로 옮겨갔다.
        for (String file : new String[] {common, local, dev}) {
            assertThat(file).doesNotContain(
                "channel-name:", "chaincode-name:", "certificate-path:", "tls-ca-path:", "override-authority:");
        }
        assertThat(Files.exists(Path.of("src/main/resources/application.properties"))).isFalse();
        assertThat(Files.list(Path.of("src/main/resources"))
            .filter(path -> path.getFileName().toString().startsWith("application"))
            .map(path -> path.getFileName().toString())
            .toList())
            .containsExactlyInAnyOrder(
                "application.yml", "application-local.yml", "application-dev.yml");
    }
}
