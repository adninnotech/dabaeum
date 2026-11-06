package com.adn.dabaeum.credential.config;

import com.adn.dabaeum.credential.application.KmsProcessIdentity;
import com.adn.dabaeum.credential.application.KmsRecoveryScheduler;
import com.adn.dabaeum.credential.application.KmsRecoveryWorker;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/** 복구 워커는 dabaeum.kms.recovery.enabled=true 이고 MyBatis 저장소가 있는 프로필에서만 돈다. */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "dabaeum.kms.recovery", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KmsRecoveryProperties.class)
@EnableScheduling
public class KmsRecoveryConfiguration {

    /** provider=local 이면 키 관리자 포트가 없어 관측 없이 경고만 한다. */
    @Bean
    KmsRecoveryWorker kmsRecoveryWorker(
        KmsOperationRepository operations,
        InstitutionSignKeyRepository keys,
        ObjectProvider<TransitKeyAdminPort> keyAdmin,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        return new KmsRecoveryWorker(operations, keys, keyAdmin.getIfAvailable(), identity, transactionManager, clock);
    }

    @Bean
    KmsRecoveryScheduler kmsRecoveryScheduler(KmsRecoveryWorker worker, KmsRecoveryProperties properties) {
        return new KmsRecoveryScheduler(worker, properties);
    }
}
