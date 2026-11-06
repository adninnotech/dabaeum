package com.adn.dabaeum.daeguchain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.blockchain.config.BlockchainProviderConfiguration;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialSigningKeyGate;
import com.adn.dabaeum.credential.application.CredentialStatusListAllocator;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningInputFactory;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.fabric.application.CredentialFabricWorker;
import com.adn.dabaeum.fabric.application.CredentialIssuanceSigner;
import com.adn.dabaeum.fabric.application.SigningKeyFinalizeCheck;
import com.adn.dabaeum.fabric.application.TransitCredentialIssuanceSigner;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** provider=openbao 면 워커가 파일 키 서명 빈 없이 Transit 서명기로 조립된다 (ADR-0006). */
class DaeguChainTransitSigningConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(BlockchainProviderConfiguration.class, DaeguChainConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(BlockchainTransactionRepository.class, () -> mock(BlockchainTransactionRepository.class))
        .withBean(CredentialRepository.class, () -> mock(CredentialRepository.class))
        .withBean(CredentialGroupRepository.class, () -> mock(CredentialGroupRepository.class))
        .withBean(CompletionRepository.class, () -> mock(CompletionRepository.class))
        .withBean(EnrollmentRepository.class, () -> mock(EnrollmentRepository.class))
        .withBean(CourseRepository.class, () -> mock(CourseRepository.class))
        .withBean(CredentialDocumentFactory.class, () -> mock(CredentialDocumentFactory.class))
        .withBean(CredentialIdentifierProvider.class, () -> mock(CredentialIdentifierProvider.class))
        .withBean(CredentialStatusListAllocator.class, () -> mock(CredentialStatusListAllocator.class))
        .withBean(CredentialHashService.class, () -> new CredentialHashService(new ObjectMapper()))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withBean(CredentialSigningRepository.class, () -> mock(CredentialSigningRepository.class))
        .withBean(InstitutionSignKeyRepository.class, () -> mock(InstitutionSignKeyRepository.class))
        .withBean(InstitutionSignKeyVersionRepository.class, () -> mock(InstitutionSignKeyVersionRepository.class))
        .withBean(CredentialSigningKeyGate.class, () -> mock(CredentialSigningKeyGate.class))
        .withBean(CredentialSigningInputFactory.class, () -> mock(CredentialSigningInputFactory.class))
        .withBean(TransitSigningPort.class, () -> mock(TransitSigningPort.class))
        .withBean(SigningKeyFinalizeCheck.class, () -> credentialId -> SigningKeyFinalizeCheck.Outcome.ALLOW)
        // 서명 경로 빈(키 게이트·MyBatis 저장소·확정 검사)은 local/dev 프로필에서만 존재한다. 프로필이
        // 없으면 withBean 으로 넣은 mock 정의도 조건 평가로 버려진다.
        .withPropertyValues(
            "spring.profiles.active=dev",
            "dabaeum.blockchain.provider=daeguchain",
            "dabaeum.blockchain.write-enabled=true",
            "dabaeum.daeguchain.base-url=http://127.0.0.1:8090",
            "dabaeum.daeguchain.project-id=EVDCFTOIGQNUVJZDSYAP");

    @Test
    void openbaoProviderAssemblesTheWorkerWithTheTransitSignerAndNoFileKey() {
        context.withPropertyValues("dabaeum.credential-proof.provider=openbao")
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).doesNotHaveBean(CredentialProofService.class);
                assertThat(result.getBean(CredentialIssuanceSigner.class))
                    .isInstanceOf(TransitCredentialIssuanceSigner.class);
                CredentialFabricWorker worker = result.getBean(CredentialFabricWorker.class);
                assertThat(ReflectionTestUtils.getField(worker, "issuanceSigner"))
                    .isSameAs(result.getBean(CredentialIssuanceSigner.class));
                assertThat(ReflectionTestUtils.getField(worker, "finalizeCheck")).isNotNull();
            });
    }

    @Test
    void localProviderKeepsTheFileKeySigner() {
        context.withPropertyValues("dabaeum.credential-proof.provider=local")
            .withBean(CredentialProofService.class, () -> mock(CredentialProofService.class))
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).doesNotHaveBean(CredentialIssuanceSigner.class);
                assertThat(ReflectionTestUtils.getField(
                    result.getBean(CredentialFabricWorker.class), "issuanceSigner")).isNull();
            });
    }
}
