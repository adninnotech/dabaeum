package com.adn.dabaeum.credential.infrastructure.crypto;

import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialSigningInputFactory;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.StatusListSigningInputFactory;
import java.nio.file.Path;
import java.security.PublicKey;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CredentialProofProperties.class)
public class CredentialProofConfiguration {

    /** 이 프로필이 활성이면 파일 키(local)로 뜨지 못한다. 지금은 prod 프로필 파일이 없어 dev 운영에 영향이 없다. */
    static final String PRODUCTION_PROFILE = "prod";

    @Bean
    InitializingBean credentialProofProviderGuard(
        Environment environment,
        CredentialProofProperties properties
    ) {
        return () -> {
            if (environment.acceptsProfiles(Profiles.of(PRODUCTION_PROFILE))
                && properties.getProvider() == CredentialProofProvider.LOCAL) {
                throw new IllegalStateException(
                    "credential-proof provider 'local' is not allowed in the '"
                        + PRODUCTION_PROFILE + "' profile (ADR-0006)");
            }
        };
    }

    @Bean
    CredentialKeyMaterialLoader credentialKeyMaterialLoader() {
        return new CredentialKeyMaterialLoader();
    }

    /**
     * openbao provider 의 signing input 생성기. 키 파일이 없어도 만들어진다. 의존 빈은 첫 호출 때 찾는다 —
     * 이 설정만 올리는 테스트 컨텍스트에는 URI 제공자가 없기 때문이다.
     */
    @Bean
    CredentialSigningInputFactory credentialSigningInputFactory(
        CredentialProofProperties properties,
        ObjectProvider<CredentialUriProvider> uriProvider,
        ObjectProvider<ObjectMapper> objectMapper
    ) {
        SingletonSupplier<Ed25519JoseCredentialProofService> service = SingletonSupplier.of(() ->
            Ed25519JoseCredentialProofService.signingInputOnly(
                properties.getKeyId(), uriProvider.getObject(), objectMapper.getObject()));
        return (document, institutionId, keyId) ->
            service.obtain().credentialSigningInput(document, institutionId, keyId);
    }

    /** openbao provider 의 Status List signing input 생성기. 키 파일이 없어도 만들어진다. */
    @Bean
    StatusListSigningInputFactory statusListSigningInputFactory(
        CredentialProofProperties properties,
        ObjectProvider<CredentialUriProvider> uriProvider,
        ObjectProvider<ObjectMapper> objectMapper
    ) {
        SingletonSupplier<Ed25519JoseCredentialProofService> service = SingletonSupplier.of(() ->
            Ed25519JoseCredentialProofService.signingInputOnly(
                properties.getKeyId(), uriProvider.getObject(), objectMapper.getObject()));
        return (document, institutionId, keyId) ->
            service.obtain().statusListSigningInput(document, institutionId, keyId);
    }

    /**
     * 버전 행 공개키로 서명을 검증한다 (설계서 §9). 키 파일이 없어도 만들어진다. 해석기는 MyBatis 저장소에 기대므로
     * local·dev 프로필에만 있고, 없으면 호출 때 KEY_UNAVAILABLE 로 실패한다.
     */
    @Bean
    CredentialSignatureVerifier credentialSignatureVerifier(
        CredentialProofProperties properties,
        ObjectProvider<CredentialUriProvider> uriProvider,
        ObjectProvider<ObjectMapper> objectMapper,
        ObjectProvider<CredentialVerificationKeyResolver> resolver
    ) {
        SingletonSupplier<Ed25519JoseCredentialProofService> service = SingletonSupplier.of(() ->
            Ed25519JoseCredentialProofService.signingInputOnly(
                properties.getKeyId(), uriProvider.getObject(), objectMapper.getObject()));
        return new CredentialSignatureVerifier() {
            @Override
            public VerifiedDocument verifyCredential(SignedCredentialEnvelope envelope) {
                return service.obtain().verifyCredential(envelope, requireResolver(resolver));
            }

            @Override
            public VerifiedDocument verifyStatusList(SignedCredentialEnvelope envelope) {
                return service.obtain().verifyStatusList(envelope, requireResolver(resolver));
            }
        };
    }

    private static CredentialVerificationKeyResolver requireResolver(
        ObjectProvider<CredentialVerificationKeyResolver> provider
    ) {
        CredentialVerificationKeyResolver resolver = provider.getIfAvailable();
        if (resolver == null) {
            throw new CredentialSignatureVerifier.VerificationFailure(
                CredentialVerificationKeyResolver.KeyResolutionException.UNAVAILABLE);
        }
        return resolver;
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
