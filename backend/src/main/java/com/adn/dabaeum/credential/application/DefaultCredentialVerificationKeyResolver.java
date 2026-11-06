package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.VerificationKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 버전 행과 그 마스터로 검증 키를 찾는다 (설계서 §9 3단계, §9.2). 버전 0 행이 없는 전환 이전 키는 설정 파일
 * 공개키로 대신한다 — 백필(계획 7) 전에도 기존 발급분이 검증되게 하기 위해서다.
 */
@Service
@Profile({"local", "dev"})
public class DefaultCredentialVerificationKeyResolver implements CredentialVerificationKeyResolver {

    private final InstitutionSignKeyVersionRepository versions;
    private final InstitutionSignKeyRepository keys;
    private final CredentialPublicJwkFactory jwks;
    private final Supplier<PublicKey> legacyFileKey;

    @Autowired
    public DefaultCredentialVerificationKeyResolver(
        InstitutionSignKeyVersionRepository versions,
        InstitutionSignKeyRepository keys,
        CredentialPublicJwkFactory jwks,
        ObjectProvider<PublicKey> legacyFileKey
    ) {
        this(versions, keys, jwks, (Supplier<PublicKey>) legacyFileKey::getIfAvailable);
    }

    public DefaultCredentialVerificationKeyResolver(
        InstitutionSignKeyVersionRepository versions,
        InstitutionSignKeyRepository keys,
        CredentialPublicJwkFactory jwks,
        Supplier<PublicKey> legacyFileKey
    ) {
        this.versions = Objects.requireNonNull(versions, "versions");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.jwks = Objects.requireNonNull(jwks, "jwks");
        this.legacyFileKey = Objects.requireNonNull(legacyFileKey, "legacyFileKey");
    }

    @Override
    public VerificationKey resolve(UUID institutionId, String kidFragment, DocumentKind kind) {
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(kind, "kind");
        if (kidFragment == null || kidFragment.isBlank()) {
            throw new KeyResolutionException(KeyResolutionException.ISSUER_MISMATCH);
        }
        boolean legacy = SignKeyNaming.LEGACY_KID_FRAGMENT.equals(kidFragment);
        Optional<InstitutionSignKeyVersion> found;
        try {
            found = versions.findByInstitutionIdAndKidFragment(institutionId, kidFragment);
        } catch (RuntimeException exception) {
            throw new KeyResolutionException(KeyResolutionException.UNAVAILABLE);
        }
        if (found.isEmpty()) {
            if (!legacy) {
                throw new KeyResolutionException(KeyResolutionException.ISSUER_MISMATCH);
            }
            PublicKey fileKey = legacyFileKey.get();
            if (fileKey == null) {
                throw new KeyResolutionException(KeyResolutionException.UNAVAILABLE);
            }
            return VerificationKey.legacyFile(fileKey);
        }
        InstitutionSignKeyVersion version = found.get();
        if (!legacy && masterPurpose(version) != expectedPurpose(kind)) {
            throw new KeyResolutionException(KeyResolutionException.PURPOSE_MISMATCH);
        }
        PublicKey publicKey;
        try {
            publicKey = jwks.publicKey(version.publicKeyJwk());
        } catch (RuntimeException exception) {
            throw new KeyResolutionException(KeyResolutionException.UNAVAILABLE);
        }
        return new VerificationKey(publicKey, version.kidFragment(), version.signKeyId(),
            version.keyVersion(), version.status(), version.compromisedAt());
    }

    private SignKeyPurpose masterPurpose(InstitutionSignKeyVersion version) {
        Optional<InstitutionSignKey> master;
        try {
            master = keys.findById(version.signKeyId());
        } catch (RuntimeException exception) {
            throw new KeyResolutionException(KeyResolutionException.UNAVAILABLE);
        }
        return master.map(InstitutionSignKey::purpose)
            .orElseThrow(() -> new KeyResolutionException(KeyResolutionException.UNAVAILABLE));
    }

    private static SignKeyPurpose expectedPurpose(DocumentKind kind) {
        return kind == DocumentKind.CREDENTIAL ? SignKeyPurpose.CREDENTIAL : SignKeyPurpose.STATUS_LIST;
    }
}
