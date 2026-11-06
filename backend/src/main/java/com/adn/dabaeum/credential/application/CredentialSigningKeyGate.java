package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialSigningException;
import com.adn.dabaeum.credential.domain.CredentialSigningException.Disposition;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 설계서 §8.1 2단계. 기관 상태, 현재 세대 수료증 키 마스터 상태, 그리고 현재 버전 행의 상태가 모두 ACTIVE
 * 인지 확인하고 헤더 kid 를 만든다. 버전 행이 COMPROMISED 면 마스터가 ACTIVE 여도 막는다 — 유출 선언 즉시
 * 동작(§6.5)이 커밋되는 순간 여기서 차단된다.
 */
@Service
@Profile({"local", "dev"})
public class CredentialSigningKeyGate {

    private final InstitutionRepository institutions;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final CredentialUriProvider uriProvider;

    public CredentialSigningKeyGate(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        CredentialUriProvider uriProvider
    ) {
        this.institutions = Objects.requireNonNull(institutions, "institutions");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
    }

    public SigningKeySelection select(UUID institutionId) {
        Objects.requireNonNull(institutionId, "institutionId");
        Institution institution = institutions.findById(institutionId).orElse(null);
        if (institution == null || institution.status() != InstitutionStatus.ACTIVE
            || institution.deletedAt() != null) {
            throw fail(CredentialSigningException.INSTITUTION_INACTIVE);
        }
        InstitutionSignKey master = keys.findCurrent(institutionId, SignKeyPurpose.CREDENTIAL)
            .filter(InstitutionSignKey::canSign)
            .orElseThrow(() -> fail(CredentialSigningException.KEY_INACTIVE));
        InstitutionSignKeyVersion version = versions
            .findBySignKeyIdAndVersion(master.id(), master.currentVersion())
            .orElseThrow(() -> fail(CredentialSigningException.KEY_INACTIVE));
        if (version.status() == SignKeyVersionStatus.COMPROMISED) {
            throw fail(CredentialSigningException.KEY_COMPROMISED);
        }
        if (version.status() != SignKeyVersionStatus.ACTIVE) {
            throw fail(CredentialSigningException.KEY_INACTIVE);
        }
        return new SigningKeySelection(master, version, uriProvider.keyId(institutionId, version.kidFragment()));
    }

    private static CredentialSigningException fail(String code) {
        return new CredentialSigningException(code, Disposition.FAIL);
    }
}
