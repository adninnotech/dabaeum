package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Transit 키 이름(설계서 §5.2)과 kid 조각(§6.2) 규칙. 두 값 모두 생성 후 바뀌지 않으며 DB 가
 * 매핑을 보관한다. 1세대는 접두를 붙이지 않아 기존 kid 가 그대로 보존된다.
 */
public final class SignKeyNaming {

    /** 전환 이전 플랫폼 키의 kid 조각. 버전 0 행에만 쓴다 (§9.2). */
    public static final String LEGACY_KID_FRAGMENT = "development-1";
    public static final int LEGACY_KEY_VERSION = 0;

    private SignKeyNaming() {
    }

    public static String keyName(UUID institutionId, int generation) {
        Objects.requireNonNull(institutionId, "institutionId");
        requirePositive(generation, "generation");
        String base = "institution-" + institutionId;
        return generation == 1 ? base : base + "-g" + generation;
    }

    public static String kidFragment(SignKeyPurpose purpose, int generation, int keyVersion) {
        Objects.requireNonNull(purpose, "purpose");
        requirePositive(generation, "generation");
        requirePositive(keyVersion, "keyVersion");
        String prefix = purpose == SignKeyPurpose.STATUS_LIST ? "status-" : "";
        String generationPart = generation == 1 ? "" : "g" + generation + "-";
        return prefix + generationPart + "v" + keyVersion;
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
