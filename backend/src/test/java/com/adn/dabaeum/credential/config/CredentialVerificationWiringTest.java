package com.adn.dabaeum.credential.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.credential.application.TransitCredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.StatusListSigningPort;
import com.adn.dabaeum.credential.infrastructure.crypto.CredentialProofConfiguration;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519JoseCredentialProofService;
import com.adn.dabaeum.credential.infrastructure.openbao.OpenBaoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/** provider=openbao 면 파일 키 없이 버전 행 검증과 Transit Status List 서명으로 조립된다 (설계서 §9, §9.1). */
class CredentialVerificationWiringTest {

    private static final String[] OPENBAO = {
        "dabaeum.credential-proof.provider=openbao",
        "dabaeum.credential-proof.openbao.signing.role-id=r1",
        "dabaeum.credential-proof.openbao.signing.secret-id=s1",
        "dabaeum.credential-proof.openbao.status.role-id=r2",
        "dabaeum.credential-proof.openbao.status.secret-id=s2",
        "dabaeum.credential-proof.openbao.key-admin.role-id=r3",
        "dabaeum.credential-proof.openbao.key-admin.secret-id=s3"
    };

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(VcConfiguration.class, CredentialProofConfiguration.class,
            OpenBaoConfiguration.class, CredentialStatusListSigningConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(InstitutionSignKeyRepository.class, () -> mock(InstitutionSignKeyRepository.class))
        .withBean(InstitutionSignKeyVersionRepository.class, () -> mock(InstitutionSignKeyVersionRepository.class))
        .withPropertyValues(
            "dabaeum.vc.public-base-url=https://vc.example.test",
            "dabaeum.vc.context-version=v1",
            "dabaeum.vc.key-version=development-1");

    @Test
    void openbao_provider_는_키_파일_없이_Transit_Status_List_서명과_버전_행_검증기를_조립한다() {
        context.withPropertyValues(OPENBAO).run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).doesNotHaveBean(CredentialProofService.class);
            assertThat(result).hasSingleBean(CredentialSignatureVerifier.class);
            assertThat(result).hasSingleBean(StatusListSigningPort.class);
            assertThat(result.getBean(CredentialStatusListProofService.class))
                .isInstanceOf(TransitCredentialStatusListProofService.class);
        });
    }

    @Test
    void 키_파일_서비스가_함께_있어도_openbao_는_Transit_Status_List_서명을_쓴다() {
        context.withPropertyValues(OPENBAO)
            .withBean(Ed25519JoseCredentialProofService.class, () -> mock(Ed25519JoseCredentialProofService.class))
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result.getBean(CredentialStatusListProofService.class))
                    .isInstanceOf(TransitCredentialStatusListProofService.class);
                assertThat(result).hasSingleBean(CredentialProofService.class);
            });
    }

    @Test
    void local_provider_는_Transit_Status_List_서명과_상태_역할_포트를_만들지_않는다() {
        context.withPropertyValues("dabaeum.credential-proof.provider=local").run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).doesNotHaveBean(CredentialStatusListProofService.class);
            assertThat(result).doesNotHaveBean(StatusListSigningPort.class);
            assertThat(result).hasSingleBean(CredentialSignatureVerifier.class);
        });
    }
}
