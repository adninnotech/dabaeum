package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.credential.domain.CredentialSigningException;
import java.util.UUID;

/**
 * 원장 제출 직전과 발급 확정(설계서 §8.1 10단계)에서 서명 키를 다시 본다. 버전 행을 FOR SHARE 로 읽으므로
 * 호출자의 트랜잭션 안에서 불러야 유출 선언(§6.5 FOR UPDATE)과 직렬화된다.
 *
 * <p>확정 경로의 잠금 순서: 호출자가 이미 {@code tb_credentials} 행을 FOR UPDATE 로 잡은 상태(재발급은 이전 → 대체)에서
 * 이 검사가 서명 버전 행을 FOR SHARE 로 잡는다. 유출 선언은 버전 행을 FOR UPDATE 로 잡은 뒤 {@code tb_credentials} 를
 * 잠그면 안 된다(교착). 원장 제출 직전 호출은 짧은 별도 트랜잭션이라 잠금이 곧 풀린다.
 */
public interface SigningKeyFinalizeCheck {

    String COMPROMISED_AT_FINALIZE = "COMPROMISED_AT_FINALIZE";
    String INACTIVE_AT_FINALIZE = "SIGNING_KEY_INACTIVE_AT_FINALIZE";

    enum Outcome {
        ALLOW,
        /** 서명한 버전 행 또는 마스터가 유출 선언됐다. */
        COMPROMISED,
        /** 마스터가 정지·폐지됐거나 기관이 활성이 아니다. */
        INACTIVE
    }

    Outcome check(UUID credentialId);

    /** 원장에 아직 아무것도 올리지 않았을 때의 실패 코드. */
    static String beforeSubmissionCode(Outcome outcome) {
        return switch (outcome) {
            case COMPROMISED -> CredentialSigningException.KEY_COMPROMISED;
            case INACTIVE -> CredentialSigningException.KEY_INACTIVE;
            case ALLOW -> throw new IllegalArgumentException("ALLOW has no failure code");
        };
    }

    /** 원장 기록 뒤 확정에서 거부됐을 때의 실패 코드. 원장 보정 대상이다. */
    static String finalizeCode(Outcome outcome) {
        return switch (outcome) {
            case COMPROMISED -> COMPROMISED_AT_FINALIZE;
            case INACTIVE -> INACTIVE_AT_FINALIZE;
            case ALLOW -> throw new IllegalArgumentException("ALLOW has no failure code");
        };
    }
}
