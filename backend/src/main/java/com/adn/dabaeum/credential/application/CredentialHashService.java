package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Service
public class CredentialHashService {

    private final ObjectMapper objectMapper;

    public CredentialHashService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String serialize(SignedCredentialEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        Map<String, String> exactEnvelope = new LinkedHashMap<>();
        exactEnvelope.put("mediaType", envelope.mediaType());
        exactEnvelope.put("compactJws", envelope.compactJws());
        return objectMapper.writer()
            .without(SerializationFeature.INDENT_OUTPUT,
                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsString(exactEnvelope);
    }

    public String sha256(SignedCredentialEnvelope envelope) {
        return sha256Payload(serialize(envelope));
    }

    /** Compact JWS 전체 문자열의 정확한 UTF-8 바이트를 해시한다. */
    public String sha256CompactJws(String compactJws) {
        return sha256Payload(Objects.requireNonNull(compactJws, "compactJws"));
    }

    /** vc_payload에 저장된 정확한 UTF-8 바이트를 해시한다. */
    public String sha256Payload(String payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }
}
