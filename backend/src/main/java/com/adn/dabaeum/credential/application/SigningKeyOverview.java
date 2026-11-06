package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsOperation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 관리자 화면의 기관 키 요약 (설계서 §14). 개인키 관련 값은 없다. */
public record SigningKeyOverview(
    UUID institutionId,
    List<KeyView> keys,
    List<KmsOperation> operations,
    List<KmsApproval> openApprovals
) {

    public record KeyView(InstitutionSignKey master, List<VersionView> versions) {
        public KeyView {
            Objects.requireNonNull(master, "master");
            versions = List.copyOf(versions);
        }
    }

    /** 버전 행과 §6.2 규칙으로 조립한 kid. */
    public record VersionView(InstitutionSignKeyVersion version, String kid) {
        public VersionView {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(kid, "kid");
        }
    }

    public SigningKeyOverview {
        Objects.requireNonNull(institutionId, "institutionId");
        keys = List.copyOf(keys);
        operations = List.copyOf(operations);
        openApprovals = List.copyOf(openApprovals);
    }
}
