package com.adn.dabaeum.attendance.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.attendance.domain.AttendanceQrTokenCodec;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenPayload;
import com.adn.dabaeum.attendance.domain.InvalidAttendanceQrTokenException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HmacAttendanceQrTokenCodecTest {

    private static final byte[] KEY = "stage5-fixed-hmac-key-for-tests-32bytes".getBytes();

    @Test
    void roundTripsPayloadAndRejectsTampering() {
        AttendanceQrTokenCodec codec = new HmacAttendanceQrTokenCodec(() -> KEY);
        AttendanceQrTokenPayload payload = payload();

        String encoded = codec.encode(payload);

        assertThat(codec.decodeAndVerify(encoded)).isEqualTo(payload);
        assertThatThrownBy(() -> codec.decodeAndVerify(
            encoded.substring(0, encoded.length() - 1)
                + (encoded.endsWith("A") ? "B" : "A")
        )).isInstanceOf(InvalidAttendanceQrTokenException.class);
        assertThat(codec.sha256(encoded)).matches("[0-9a-f]{64}");
    }

    @Test
    void payloadUsesThirtyTwoByteNonce() {
        AttendanceQrTokenPayload payload = payload();

        assertThat(Base64.getUrlDecoder().decode(payload.nonce())).hasSize(32);
    }

    private AttendanceQrTokenPayload payload() {
        return new AttendanceQrTokenPayload(
            1,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            Instant.parse("2026-08-05T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:30Z"),
            Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32])
        );
    }
}
