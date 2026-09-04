package com.adn.dabaeum.attendance.infrastructure.crypto;

import java.security.SecureRandom;
import java.util.Arrays;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev", "integration-test"})
public class EphemeralAttendanceQrKeyProvider implements AttendanceQrKeyProvider {

    private final byte[] key;

    public EphemeralAttendanceQrKeyProvider() {
        key = new byte[32];
        new SecureRandom().nextBytes(key);
    }

    @Override
    public byte[] key() {
        return Arrays.copyOf(key, key.length);
    }
}
