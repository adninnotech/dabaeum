package com.adn.dabaeum.credential.config;

import com.adn.dabaeum.credential.application.TransitCredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialStatusListProofService;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.StatusListSigningInputFactory;
import com.adn.dabaeum.credential.domain.StatusListSigningPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * provider=openbao 면 Status List 를 기관별 STATUS_LIST 키로 Transit 서명한다 (설계서 §9.1). 키 파일도 설정돼
 * 있으면 파일 키 서비스가 같은 인터페이스를 구현하므로 이 빈을 우선한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dabaeum.credential-proof", name = "provider", havingValue = "openbao")
public class CredentialStatusListSigningConfiguration {

    @Bean
    @Primary
    CredentialStatusListProofService transitCredentialStatusListProofService(
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        CredentialUriProvider uriProvider,
        StatusListSigningInputFactory inputs,
        StatusListSigningPort transit,
        CredentialSignatureVerifier verifier
    ) {
        return new TransitCredentialStatusListProofService(keys, versions, uriProvider, inputs, transit, verifier);
    }
}
