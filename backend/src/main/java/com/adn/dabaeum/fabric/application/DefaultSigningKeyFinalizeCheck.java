package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "dev"})
public class DefaultSigningKeyFinalizeCheck implements SigningKeyFinalizeCheck {

    private final CredentialSigningRepository signing;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final InstitutionRepository institutions;

    public DefaultSigningKeyFinalizeCheck(
        CredentialSigningRepository signing,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        InstitutionRepository institutions
    ) {
        this.signing = Objects.requireNonNull(signing, "signing");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.institutions = Objects.requireNonNull(institutions, "institutions");
    }

    @Override
    public Outcome check(UUID credentialId) {
        CredentialSigningState state = signing.findSigningState(credentialId).orElse(null);
        if (state == null || state.signKeyId() == null) {
            return Outcome.ALLOW;
        }
        // 버전 행을 먼저 FOR SHARE 로 잡는다. 유출 선언이 이 행을 FOR UPDATE 로 잡는다.
        SignKeyVersionStatus version = versions.findStatusForShare(state.signKeyId(), state.signKeyVersion())
            .orElseThrow(() -> new IllegalStateException("Signing key version is missing"));
        InstitutionSignKey master = keys.findById(state.signKeyId())
            .orElseThrow(() -> new IllegalStateException("Signing key is missing"));
        if (version == SignKeyVersionStatus.COMPROMISED || master.status() == SignKeyStatus.COMPROMISED) {
            return Outcome.COMPROMISED;
        }
        if (master.status() == SignKeyStatus.SUSPENDED || master.status() == SignKeyStatus.REVOKED) {
            return Outcome.INACTIVE;
        }
        Institution institution = institutions.findById(master.institutionId()).orElse(null);
        if (institution == null || institution.status() != InstitutionStatus.ACTIVE
            || institution.deletedAt() != null) {
            return Outcome.INACTIVE;
        }
        return Outcome.ALLOW;
    }
}
