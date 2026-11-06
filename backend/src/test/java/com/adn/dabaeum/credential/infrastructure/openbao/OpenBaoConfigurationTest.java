package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.StatusListSigningPort;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import com.adn.dabaeum.credential.infrastructure.crypto.CredentialProofConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

/** 설계서 §13.1: 백엔드 하나가 AppRole 세 개로 붙는다. 빈 생성은 OpenBao 에 접속하지 않는다. */
class OpenBaoConfigurationTest {

    private static final String[] ALL_ROLES = {
        "dabaeum.credential-proof.openbao.signing.role-id=r1",
        "dabaeum.credential-proof.openbao.signing.secret-id=s1",
        "dabaeum.credential-proof.openbao.status.role-id=r2",
        "dabaeum.credential-proof.openbao.status.secret-id=s2",
        "dabaeum.credential-proof.openbao.key-admin.role-id=r3",
        "dabaeum.credential-proof.openbao.key-admin.secret-id=s3"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CredentialProofConfiguration.class, OpenBaoConfiguration.class);

    @Test
    void provider_local_이면_OpenBao_클라이언트를_만들지_않는다() {
        runner.withPropertyValues("dabaeum.credential-proof.provider=local")
            .run(context -> {
                assertThat(context).doesNotHaveBean(OpenBaoClients.class);
                assertThat(context).doesNotHaveBean(TransitSigningPort.class);
                assertThat(context).doesNotHaveBean(StatusListSigningPort.class);
                assertThat(context).doesNotHaveBean(TransitKeyAdminPort.class);
            });
    }

    @Test
    void provider_openbao_면_역할별_VaultTemplate_셋을_만든다() {
        runner.withPropertyValues("dabaeum.credential-proof.provider=openbao")
            .withPropertyValues(ALL_ROLES)
            .run(context -> {
                assertThat(context).hasSingleBean(OpenBaoClients.class);
                OpenBaoClients clients = context.getBean(OpenBaoClients.class);
                assertThat(clients.signing()).isNotNull();
                assertThat(clients.status()).isNotNull();
                assertThat(clients.keyAdmin()).isNotNull();
                assertThat(clients.signing()).isNotSameAs(clients.status());
                assertThat(clients.status()).isNotSameAs(clients.keyAdmin());
            });
    }

    @Test
    void 역할_하나라도_role_id_또는_secret_id_가_비면_기동을_거부한다() {
        runner.withPropertyValues(
                "dabaeum.credential-proof.provider=openbao",
                "dabaeum.credential-proof.openbao.signing.role-id=r1",
                "dabaeum.credential-proof.openbao.signing.secret-id=s1",
                "dabaeum.credential-proof.openbao.status.role-id=r2",
                // status.secret-id 누락
                "dabaeum.credential-proof.openbao.key-admin.role-id=r3",
                "dabaeum.credential-proof.openbao.key-admin.secret-id=s3")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("openbao status role-id and secret-id are required");
            });
    }

    @Test
    void provider_openbao_면_Transit_서명_포트를_만든다() {
        runner.withPropertyValues("dabaeum.credential-proof.provider=openbao")
            .withPropertyValues(ALL_ROLES)
            .run(context -> {
                assertThat(context).hasSingleBean(TransitSigningPort.class);
                assertThat(context).hasSingleBean(StatusListSigningPort.class);
                assertThat(context).hasSingleBean(TransitKeyAdminPort.class);
            });
    }

    /**
     * 토큰 갱신 스케줄러는 OpenBao 내부용이다. 컨텍스트 빈으로 노출되면 다른 모듈의 TaskScheduler 타입 주입
     * (출결 QR 의 dadaeguAttendTaskScheduler 등)이 후보 2개로 기동 실패한다 — 2026-09-16 S3 스위치에서 실제 발생.
     */
    @Test
    void 토큰_갱신_스케줄러는_다른_모듈의_TaskScheduler_주입을_방해하지_않는다() {
        runner.withPropertyValues("dabaeum.credential-proof.provider=openbao")
            .withPropertyValues(ALL_ROLES)
            .withUserConfiguration(OtherModuleScheduler.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(TaskScheduler.class);
                assertThat(context.getBean(SchedulerConsumer.class).scheduler())
                    .isSameAs(context.getBean("otherModuleScheduler"));
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class OtherModuleScheduler {

        @Bean
        TaskScheduler otherModuleScheduler() {
            return new SimpleAsyncTaskScheduler();
        }

        @Bean
        SchedulerConsumer schedulerConsumer(TaskScheduler scheduler) {
            return new SchedulerConsumer(scheduler);
        }
    }

    record SchedulerConsumer(TaskScheduler scheduler) {
    }
}
