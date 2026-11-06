package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 설계서 §13.2, §17-11: 운영 프로필에서 파일 키(local)로 뜨는 것을 막는다. */
class CredentialProofProviderGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CredentialProofConfiguration.class);

    @Test
    void 운영_프로필에서_provider_local_이면_기동을_거부한다() {
        runner.withPropertyValues(
                "spring.profiles.active=prod",
                "dabaeum.credential-proof.provider=local")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("provider 'local' is not allowed in the 'prod' profile");
            });
    }

    @Test
    void 운영_프로필에서_provider_openbao_는_허용한다() {
        runner.withPropertyValues(
                "spring.profiles.active=prod",
                "dabaeum.credential-proof.provider=openbao")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 개발_프로필에서는_provider_local_을_허용한다() {
        runner.withPropertyValues(
                "spring.profiles.active=dev",
                "dabaeum.credential-proof.provider=local")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 프로필이_없으면_provider_local_을_허용한다() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
