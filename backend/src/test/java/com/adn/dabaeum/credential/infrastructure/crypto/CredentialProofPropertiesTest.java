package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CredentialProofPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CredentialProofProperties.class)
    static class Binding {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(Binding.class);

    @Test
    void provider_기본값은_local_이고_openbao_설정은_기본값을_가진다() {
        runner.run(context -> {
            CredentialProofProperties properties = context.getBean(CredentialProofProperties.class);
            assertThat(properties.getProvider()).isEqualTo(CredentialProofProvider.LOCAL);
            assertThat(properties.getOpenbao().getUri()).isEqualTo("http://127.0.0.1:8200");
            assertThat(properties.getOpenbao().getSignMount()).isEqualTo("education-sign");
            assertThat(properties.getOpenbao().getStatusMount()).isEqualTo("education-status");
            assertThat(properties.getOpenbao().getAppRolePath()).isEqualTo("approle");
            assertThat(properties.getOpenbao().getSigning().getRoleId()).isEmpty();
            assertThat(properties.getOpenbao().getSigning().getSecretId()).isEmpty();
        });
    }

    @Test
    void provider_openbao_와_역할별_appRole_을_바인딩한다() {
        runner.withPropertyValues(
                "dabaeum.credential-proof.provider=openbao",
                "dabaeum.credential-proof.openbao.uri=http://127.0.0.1:8200",
                "dabaeum.credential-proof.openbao.signing.role-id=r1",
                "dabaeum.credential-proof.openbao.signing.secret-id=s1",
                "dabaeum.credential-proof.openbao.status.role-id=r2",
                "dabaeum.credential-proof.openbao.status.secret-id=s2",
                "dabaeum.credential-proof.openbao.key-admin.role-id=r3",
                "dabaeum.credential-proof.openbao.key-admin.secret-id=s3")
            .run(context -> {
                CredentialProofProperties properties = context.getBean(CredentialProofProperties.class);
                assertThat(properties.getProvider()).isEqualTo(CredentialProofProvider.OPENBAO);
                assertThat(properties.getOpenbao().getSigning().getRoleId()).isEqualTo("r1");
                assertThat(properties.getOpenbao().getStatus().getSecretId()).isEqualTo("s2");
                assertThat(properties.getOpenbao().getKeyAdmin().getRoleId()).isEqualTo("r3");
            });
    }

    @Test
    void 알_수_없는_provider_는_기동을_거부한다() {
        runner.withPropertyValues("dabaeum.credential-proof.provider=vault")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 기존_key_id_검증은_그대로다() {
        runner.withPropertyValues("dabaeum.credential-proof.key-id=urn:dabaeum:credential-key:other")
            .run(context -> assertThat(context).hasFailed());
    }
}
