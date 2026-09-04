package com.adn.dabaeum.credential.infrastructure.crypto;

import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import java.nio.file.Path;
import java.security.PublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CredentialProofProperties.class)
public class CredentialProofConfiguration {

    @Bean
    CredentialKeyMaterialLoader credentialKeyMaterialLoader() {
        return new CredentialKeyMaterialLoader();
    }

    @Bean
    @Conditional(ConfiguredKeyPathsCondition.class)
    CredentialKeyMaterialLoader.KeyMaterial credentialKeyMaterial(
        CredentialProofProperties properties,
        CredentialKeyMaterialLoader loader
    ) {
        return loader.load(
            Path.of(properties.getPrivateKeyPath()), Path.of(properties.getPublicKeyPath()));
    }

    // 구체 타입으로 노출해 CredentialProofService 와 CredentialStatusListProofService 둘 다로 주입된다.
    @Bean
    @Conditional(ConfiguredKeyPathsCondition.class)
    Ed25519JoseCredentialProofService credentialProofService(
        CredentialProofProperties properties,
        CredentialUriProvider uriProvider,
        CredentialKeyMaterialLoader.KeyMaterial material,
        ObjectMapper objectMapper
    ) {
        return new Ed25519JoseCredentialProofService(properties.getKeyId(), uriProvider,
            material.privateKey(), material.publicKey(), objectMapper);
    }

    @Bean
    @Conditional(ConfiguredPublicKeyPathCondition.class)
    PublicKey credentialProofPublicKey(
        CredentialProofProperties properties,
        CredentialKeyMaterialLoader loader
    ) {
        return loader.loadPublic(Path.of(properties.getPublicKeyPath()));
    }

    static final class ConfiguredKeyPathsCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(
                "dabaeum.credential-proof.private-key-path"))
                && StringUtils.hasText(context.getEnvironment().getProperty(
                    "dabaeum.credential-proof.public-key-path"));
        }
    }

    static final class ConfiguredPublicKeyPathCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(
                "dabaeum.credential-proof.public-key-path"));
        }
    }
}
