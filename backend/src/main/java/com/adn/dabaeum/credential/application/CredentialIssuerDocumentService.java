package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 기관 발급자 문서 (W3C CID, 설계서 §9.2). 수료증·Status List 키의 COMPROMISED 가 아닌 모든 버전을
 * verificationMethod·assertionMethod 양쪽에 싣는다. 회전 T2 커밋이 공개 시점이므로 캐시 없이 DB 를 읽는다.
 * 버전 0 행이 없는(백필 전) 기관은 설정 파일 공개키를 전환 이전 kid 로 싣는다.
 */
@Service
public class CredentialIssuerDocumentService {

    private static final Comparator<Published> ORDER = Comparator
        .comparingInt((Published key) -> key.purpose() == SignKeyPurpose.CREDENTIAL ? 0 : 1)
        .thenComparing(Published::generation, Comparator.reverseOrder())
        .thenComparing(Published::keyVersion, Comparator.reverseOrder());

    private final InstitutionRepository institutions;
    private final CredentialUriProvider uriProvider;
    private final ObjectProvider<PublicKey> publicKeyProvider;
    private final CredentialPublicJwkFactory jwkFactory;
    private final ObjectProvider<InstitutionSignKeyRepository> keys;
    private final ObjectProvider<InstitutionSignKeyVersionRepository> versions;

    public CredentialIssuerDocumentService(
        InstitutionRepository institutions,
        CredentialUriProvider uriProvider,
        ObjectProvider<PublicKey> publicKeyProvider,
        CredentialPublicJwkFactory jwkFactory,
        ObjectProvider<InstitutionSignKeyRepository> keys,
        ObjectProvider<InstitutionSignKeyVersionRepository> versions
    ) {
        this.institutions = Objects.requireNonNull(institutions, "institutions");
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
        this.publicKeyProvider = Objects.requireNonNull(publicKeyProvider, "publicKeyProvider");
        this.jwkFactory = Objects.requireNonNull(jwkFactory, "jwkFactory");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.versions = Objects.requireNonNull(versions, "versions");
    }

    public Map<String, Object> document(UUID institutionId) {
        Objects.requireNonNull(institutionId, "institutionId");
        institutions.findById(institutionId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.INSTITUTION_NOT_FOUND, "Institution not found"));

        String issuer = uriProvider.issuerUrl(institutionId);
        List<Map<String, String>> jwks = publishedJwks(institutionId);
        if (jwks.isEmpty()) {
            throw unavailable();
        }
        List<Map<String, Object>> verificationMethods = new ArrayList<>();
        List<String> assertionMethods = new ArrayList<>();
        for (Map<String, String> jwk : jwks) {
            Map<String, Object> verificationMethod = new LinkedHashMap<>();
            verificationMethod.put("id", jwk.get("kid"));
            verificationMethod.put("type", "JsonWebKey");
            verificationMethod.put("controller", issuer);
            verificationMethod.put("publicKeyJwk", jwk);
            verificationMethods.add(verificationMethod);
            assertionMethods.add(jwk.get("kid"));
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", "https://www.w3.org/ns/cid/v1");
        document.put("id", issuer);
        document.put("verificationMethod", List.copyOf(verificationMethods));
        document.put("assertionMethod", List.copyOf(assertionMethods));
        return document;
    }

    private List<Map<String, String>> publishedJwks(UUID institutionId) {
        List<Published> published = new ArrayList<>();
        boolean legacyRowExists = false;
        InstitutionSignKeyVersionRepository versionRepository = versions.getIfAvailable();
        InstitutionSignKeyRepository keyRepository = keys.getIfAvailable();
        if (versionRepository != null && keyRepository != null) {
            List<InstitutionSignKeyVersion> rows;
            Map<UUID, InstitutionSignKey> masters = new HashMap<>();
            try {
                rows = versionRepository.findByInstitutionId(institutionId);
                for (InstitutionSignKey master : keyRepository.findByInstitutionId(institutionId)) {
                    masters.put(master.id(), master);
                }
            } catch (RuntimeException exception) {
                throw unavailable();
            }
            for (InstitutionSignKeyVersion row : rows) {
                if (SignKeyNaming.LEGACY_KID_FRAGMENT.equals(row.kidFragment())) {
                    legacyRowExists = true;
                }
                if (!row.isPublished()) {
                    continue;
                }
                InstitutionSignKey master = masters.get(row.signKeyId());
                if (master == null) {
                    throw unavailable();
                }
                published.add(new Published(master.purpose(), master.generation(), row.keyVersion(),
                    publish(uriProvider.keyId(institutionId, row.kidFragment()), row.publicKeyJwk())));
            }
        }
        if (!legacyRowExists) {
            PublicKey fileKey = publicKeyProvider.getIfAvailable();
            if (fileKey != null) {
                published.add(new Published(SignKeyPurpose.CREDENTIAL, 1, SignKeyNaming.LEGACY_KEY_VERSION,
                    fileJwk(uriProvider.keyId(institutionId), fileKey)));
            }
        }
        published.sort(ORDER);
        return published.stream().map(Published::jwk).toList();
    }

    private Map<String, String> publish(String keyId, String storedJwk) {
        try {
            return jwkFactory.publish(keyId, storedJwk);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private Map<String, String> fileJwk(String keyId, PublicKey publicKey) {
        try {
            return jwkFactory.create(keyId, publicKey);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            ApiErrorCode.INTERNAL_SERVER_ERROR, "Credential issuer key is unavailable");
    }

    private record Published(
        SignKeyPurpose purpose,
        int generation,
        int keyVersion,
        Map<String, String> jwk
    ) {
    }
}
