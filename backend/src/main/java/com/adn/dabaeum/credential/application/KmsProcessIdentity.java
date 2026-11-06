package com.adn.dabaeum.credential.application;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** 작업 소유권의 프로세스 인스턴스 식별자 (설계서 §11.4, §15.1). 같은 호스트에서 재기동하면 값이 바뀐다. */
public record KmsProcessIdentity(String owner) {

    private static final int MAX_HOST_LENGTH = 80;

    /**
     * 소유권 lease (§11.4, §21 확정 5분). 획득·재확인·인계 판단이 모두 이 값을 쓰고, 복구 워커의 기본
     * fixed-delay(application.yml `dabaeum.kms.recovery.fixed-delay: 5m`)도 같은 크기다. 한 곳만 바꾸면 안 된다.
     */
    public static final Duration LEASE = Duration.ofMinutes(5);

    public KmsProcessIdentity {
        Objects.requireNonNull(owner, "owner");
        if (owner.isBlank() || owner.length() > 100) {
            throw new IllegalArgumentException("owner must be 1..100 characters");
        }
    }

    public static KmsProcessIdentity generate() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            host = "unknown";
        }
        if (host == null || host.isBlank()) {
            host = "unknown";
        }
        if (host.length() > MAX_HOST_LENGTH) {
            host = host.substring(0, MAX_HOST_LENGTH);
        }
        byte[] random = new byte[8];
        new SecureRandom().nextBytes(random);
        return new KmsProcessIdentity(host + "-" + HexFormat.of().formatHex(random));
    }
}
