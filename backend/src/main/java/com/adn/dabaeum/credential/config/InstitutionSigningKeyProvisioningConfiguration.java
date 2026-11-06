package com.adn.dabaeum.credential.config;

import com.adn.dabaeum.credential.application.InstitutionSigningKeyProvisioner;
import com.adn.dabaeum.credential.application.InstitutionSigningKeyScheduler;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningService;
import com.adn.dabaeum.credential.domain.SigningKeyRequestQueue;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 기관 승인·생성 뒤 서명키 자동 생성 워커. provider=openbao 일 때만 돈다(local 이면 요청만 쌓인다).
 * dabaeum.kms.provisioning.enabled=false 로 끌 수 있다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "dabaeum.credential-proof", name = "provider", havingValue = "openbao")
@EnableScheduling
public class InstitutionSigningKeyProvisioningConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.kms.provisioning", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    InstitutionSigningKeyProvisioner institutionSigningKeyProvisioner(
        SigningKeyRequestQueue queue,
        SigningKeyProvisioningService provisioning,
        TransitKeyAdminPort keyAdmin,
        Clock clock
    ) {
        return new InstitutionSigningKeyProvisioner(queue, provisioning, keyAdmin, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.kms.provisioning", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    InstitutionSigningKeyScheduler institutionSigningKeyScheduler(
        InstitutionSigningKeyProvisioner provisioner
    ) {
        return new InstitutionSigningKeyScheduler(provisioner);
    }
}
