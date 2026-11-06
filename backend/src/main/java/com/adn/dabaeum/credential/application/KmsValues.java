package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

/** 작업 기록의 before/after 값과 버전 행의 보존 JWK. 비밀값을 만들지 않는다 (설계서 §11.4). */
final class KmsValues {

    private KmsValues() {
    }

    /** {"status":"ACTIVE","currentVersion":3,"generation":1,"keyName":"institution-…"} */
    static String master(InstitutionSignKey master) {
        return "{\"status\":\"" + master.status().name() + "\",\"currentVersion\":" + master.currentVersion()
            + ",\"generation\":" + master.generation() + ",\"keyName\":\"" + master.keyName() + "\"}";
    }

    /** 유출 선언 작업의 before/after. 버전 번호와 상태만 (비밀값 없음). */
    static String version(InstitutionSignKeyVersion version) {
        return "{\"keyVersion\":" + version.keyVersion() + ",\"status\":\"" + version.status().name() + "\"}";
    }

    /** kid 없는 JWK (§6.2 규칙으로 조회 시 조립). 필드는 kty·crv·alg·use·x 다섯 개다. */
    static String storedJwk(CredentialPublicJwkFactory jwks, PublicKey publicKey) {
        Map<String, String> jwk = new LinkedHashMap<>(jwks.create("kid-placeholder", publicKey));
        jwk.remove("kid");
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : jwk.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        return json.append('}').toString();
    }
}
