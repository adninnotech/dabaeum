package com.adn.dabaeum.credential.infrastructure.openbao;

import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException.Reason;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.VaultLoginException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

/**
 * 키 관리자 역할 토큰으로 {@code {mount}/keys/{name}}, {@code …/rotate}, {@code …/config} 를 부른다 (설계서 §13.4).
 * 생성 요청은 type 만 보낸다 — exportable 등은 Policy 가 값과 무관하게 거부하고 기본값이 모두 false 다 (§5.3).
 */
public final class OpenBaoTransitKeyAdminAdapter implements TransitKeyAdminPort {

    private static final Pattern PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,149}$");
    /** RFC 8410 SubjectPublicKeyInfo 접두: SEQUENCE { AlgorithmIdentifier(id-Ed25519), BIT STRING }. */
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final int RAW_KEY_LENGTH = 32;

    private final VaultOperations keyAdmin;

    public OpenBaoTransitKeyAdminAdapter(VaultOperations keyAdmin) {
        this.keyAdmin = Objects.requireNonNull(keyAdmin, "keyAdmin");
    }

    @Override
    public void createKey(String mount, String keyName) {
        String path = keyPath(mount, keyName);
        call(() -> keyAdmin.write(path, Map.of("type", TransitKeyInfo.ED25519)), "create");
    }

    @Override
    public Optional<TransitKeyInfo> readKey(String mount, String keyName) {
        String path = keyPath(mount, keyName);
        VaultResponse response = call(() -> keyAdmin.read(path), "read");
        if (response == null || response.getData() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(response.getData()));
        } catch (RuntimeException exception) {
            throw new TransitKeyAdminException(Reason.REJECTED, "OpenBao key response is malformed", exception);
        }
    }

    @Override
    public void rotateKey(String mount, String keyName) {
        String path = keyPath(mount, keyName) + "/rotate";
        call(() -> keyAdmin.write(path), "rotate");
    }

    @Override
    public void setMinEncryptionVersion(String mount, String keyName, int minEncryptionVersion) {
        if (minEncryptionVersion < 1) {
            throw new IllegalArgumentException("minEncryptionVersion must be positive");
        }
        String path = keyPath(mount, keyName) + "/config";
        call(() -> keyAdmin.write(path, Map.of("min_encryption_version", minEncryptionVersion)), "config");
    }

    private interface Call {
        VaultResponse run();
    }

    private static VaultResponse call(Call call, String operation) {
        try {
            return call.run();
        } catch (VaultException exception) {
            throw classify(exception, operation);
        } catch (RestClientException exception) {
            throw new TransitKeyAdminException(Reason.UNAVAILABLE, "OpenBao is unreachable", exception);
        }
    }

    private static TransitKeyInfo parse(Map<String, Object> data) {
        Map<Integer, TransitKeyInfo.Version> versions = new HashMap<>();
        Object keys = data.get("keys");
        if (keys instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<?, ?> version = (Map<?, ?>) entry.getValue();
                versions.put(Integer.parseInt(String.valueOf(entry.getKey())), new TransitKeyInfo.Version(
                    parsePublicKey(String.valueOf(version.get("public_key"))),
                    Instant.parse(String.valueOf(version.get("creation_time")))));
            }
        }
        return new TransitKeyInfo(
            String.valueOf(data.get("type")),
            bool(data.get("exportable")),
            bool(data.get("allow_plaintext_backup")),
            bool(data.get("deletion_allowed")),
            bool(data.get("derived")),
            number(data.get("latest_version")).intValue(),
            number(data.getOrDefault("min_encryption_version", 0)).intValue(),
            number(data.getOrDefault("auto_rotate_period", 0)).longValue(),
            versions);
    }

    /** Transit 은 ed25519 공개키를 PEM 또는 raw 32바이트의 base64 로 돌려준다. 둘 다 받는다 (계획 1 실측). */
    private static PublicKey parsePublicKey(String publicKey) {
        try {
            byte[] encoded;
            if (publicKey.startsWith("-----BEGIN")) {
                encoded = Base64.getMimeDecoder().decode(publicKey.replaceAll("-----[A-Z ]+-----", ""));
            } else {
                byte[] raw = Base64.getDecoder().decode(publicKey);
                if (raw.length == RAW_KEY_LENGTH) {
                    encoded = new byte[SPKI_PREFIX.length + raw.length];
                    System.arraycopy(SPKI_PREFIX, 0, encoded, 0, SPKI_PREFIX.length);
                    System.arraycopy(raw, 0, encoded, SPKI_PREFIX.length, raw.length);
                } else {
                    encoded = raw;
                }
            }
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalArgumentException("public key is not an Ed25519 key", exception);
        }
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : Long.parseLong(String.valueOf(value));
    }

    private static TransitKeyAdminException classify(VaultException exception, String operation) {
        if (exception instanceof VaultLoginException) {
            // 로그인 400(만료된 Secret ID)을 키 거부로 보면 서명키 자동 생성이 재시도 없이 끝난다(QA INST-03).
            return new TransitKeyAdminException(
                Reason.AUTHENTICATION_FAILED, "OpenBao login failed before the key " + operation, exception);
        }
        HttpStatusCodeException http = httpCause(exception);
        if (http == null) {
            return new TransitKeyAdminException(Reason.UNAVAILABLE, "OpenBao key " + operation + " failed", exception);
        }
        int status = http.getStatusCode().value();
        if (status == 403 || status == 429 || status >= 500) {
            return new TransitKeyAdminException(
                Reason.UNAVAILABLE, "OpenBao is unavailable (" + status + ")", exception);
        }
        return new TransitKeyAdminException(
            Reason.REJECTED, "OpenBao rejected the key " + operation + " (" + status + ")", exception);
    }

    private static HttpStatusCodeException httpCause(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException http) {
                return http;
            }
        }
        return null;
    }

    private static String keyPath(String mount, String keyName) {
        requireSegment(mount, "mount");
        requireSegment(keyName, "keyName");
        return mount + "/keys/" + keyName;
    }

    private static void requireSegment(String value, String name) {
        if (value == null || !PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
