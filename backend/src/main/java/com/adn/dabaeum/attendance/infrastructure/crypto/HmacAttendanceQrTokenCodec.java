package com.adn.dabaeum.attendance.infrastructure.crypto;

import com.adn.dabaeum.attendance.domain.AttendanceQrTokenCodec;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenPayload;
import com.adn.dabaeum.attendance.domain.InvalidAttendanceQrTokenException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(AttendanceQrKeyProvider.class)
public class HmacAttendanceQrTokenCodec implements AttendanceQrTokenCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final AttendanceQrKeyProvider keyProvider;

    public HmacAttendanceQrTokenCodec(AttendanceQrKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public String encode(AttendanceQrTokenPayload payload) {
        String canonical = String.join("|",
            Integer.toString(payload.version()),
            payload.tokenId().toString(),
            payload.sessionId().toString(),
            Long.toString(payload.issuedAt().toEpochMilli()),
            Long.toString(payload.expiresAt().toEpochMilli()),
            payload.nonce());
        String encodedPayload = BASE64_ENCODER.encodeToString(
            canonical.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + BASE64_ENCODER.encodeToString(sign(encodedPayload));
    }

    @Override
    public AttendanceQrTokenPayload decodeAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidAttendanceQrTokenException();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidAttendanceQrTokenException();
        }
        try {
            byte[] actualSignature = BASE64_DECODER.decode(parts[1]);
            byte[] expectedSignature = sign(parts[0]);
            if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
                throw new InvalidAttendanceQrTokenException();
            }
            String canonical = new String(BASE64_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = canonical.split("\\|", -1);
            if (fields.length != 6) {
                throw new InvalidAttendanceQrTokenException();
            }
            return new AttendanceQrTokenPayload(
                Integer.parseInt(fields[0]),
                UUID.fromString(fields[1]),
                UUID.fromString(fields[2]),
                Instant.ofEpochMilli(Long.parseLong(fields[3])),
                Instant.ofEpochMilli(Long.parseLong(fields[4])),
                fields[5]
            );
        } catch (InvalidAttendanceQrTokenException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAttendanceQrTokenException();
        }
    }

    @Override
    public String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyProvider.key(), HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("QR token signing is unavailable", exception);
        }
    }
}
