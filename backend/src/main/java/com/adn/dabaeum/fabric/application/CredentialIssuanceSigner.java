package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 파일 키 대신 쓰는 발급 서명 (ADR-0006). 서명 입력의 사전 커밋부터 조건부 저장까지를 맡고, 원장 제출과
 * 발급 확정은 워커가 한다.
 */
public interface CredentialIssuanceSigner {

    /** 봉투가 저장된 ISSUING 수료증과 그 봉투. */
    record Signed(Credential credential, SignedCredentialEnvelope envelope) {

        public Signed {
            Objects.requireNonNull(credential, "credential");
            Objects.requireNonNull(envelope, "envelope");
            if (credential.vcPayload() == null || credential.vcHash() == null) {
                throw new IllegalArgumentException("signed credential must have a stored envelope");
            }
        }
    }

    /**
     * @param context 서명 입력을 아직 커밋하지 않았을 때만 부른다. 재시도는 저장된 입력을 쓴다
     * @param vcHash 봉투의 원장 해시. 레거시·현재 규칙은 워커가 정한다
     * @throws com.adn.dabaeum.credential.domain.CredentialSigningException 처리 방식이 담긴 실패
     */
    Signed sign(
        UUID credentialId,
        Supplier<CredentialFabricIssuanceContext> context,
        Function<SignedCredentialEnvelope, String> vcHash,
        Instant now);
}
