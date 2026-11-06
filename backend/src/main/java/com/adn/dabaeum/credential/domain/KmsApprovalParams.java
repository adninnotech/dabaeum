package com.adn.dabaeum.credential.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 승인이 묶는 실행 파라미터의 해시 (설계서 §12.2). 승인 요청과 실행 요청이 같은 값을 보내야 한다. reason 은
 * 제외하고, 대상 버전·사고 시각은 별도 컬럼으로 대조한다.
 */
public final class KmsApprovalParams {

    private KmsApprovalParams() {
    }

    public static String hash(KmsAction action, SignKeyPurpose purpose, String idempotencyKey) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(purpose, "purpose");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        String canonical = action.name() + "\n" + purpose.name() + "\n" + idempotencyKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
