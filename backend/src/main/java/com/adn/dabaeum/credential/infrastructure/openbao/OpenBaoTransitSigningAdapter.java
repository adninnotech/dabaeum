package com.adn.dabaeum.credential.infrastructure.openbao;

import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import com.adn.dabaeum.credential.domain.TransitSigningException.Reason;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

/**
 * 발급 역할 토큰으로 {@code {mount}/sign/{keyName}} 을 부른다. 요청 본문은 input 과 key_version 뿐이다.
 * prehashed·hash_algorithm 은 보내지 않는다 — OpenBao 2.6.2 는 ed25519 에서 두 파라미터를 무시하므로
 * 다이제스트를 넘기면 오류 없이 다이제스트 바이트에 서명한다 (계획 1 Task 6 실측).
 */
public final class OpenBaoTransitSigningAdapter implements TransitSigningPort {

    private static final Pattern SIGNATURE =
        Pattern.compile("^vault:v([1-9][0-9]{0,8}):([A-Za-z0-9+/]+={0,2})$");
    private static final Pattern PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,149}$");

    /**
     * min_encryption_version 거부 문구. OpenBao 2.6.2 는 이 거부를 HTTP 500 으로 돌려주므로 상태 코드가 아니라
     * 문구로 판별한다 (계획 3 Task 2 실측).
     */
    private static final String MIN_VERSION_REJECTION = "less than the minimum encryption key version";

    private final VaultOperations signing;

    public OpenBaoTransitSigningAdapter(VaultOperations signing) {
        this.signing = Objects.requireNonNull(signing, "signing");
    }

    @Override
    public TransitSignature sign(String mount, String keyName, int keyVersion, byte[] signingInput) {
        requireSegment(mount, "mount");
        requireSegment(keyName, "keyName");
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        Objects.requireNonNull(signingInput, "signingInput");
        VaultResponse response;
        try {
            response = signing.write(mount + "/sign/" + keyName, Map.of(
                "input", Base64.getEncoder().encodeToString(signingInput),
                "key_version", keyVersion));
        } catch (VaultException exception) {
            throw classify(exception);
        } catch (RestClientException exception) {
            throw new TransitSigningException(Reason.UNAVAILABLE, "OpenBao is unreachable", exception);
        }
        return parse(response);
    }

    private static TransitSignature parse(VaultResponse response) {
        Object value = response == null || response.getData() == null
            ? null : response.getData().get("signature");
        if (!(value instanceof String text)) {
            throw new TransitSigningException(Reason.REJECTED, "OpenBao sign response has no signature", null);
        }
        Matcher matcher = SIGNATURE.matcher(text);
        if (!matcher.matches()) {
            throw new TransitSigningException(Reason.REJECTED, "OpenBao sign response is malformed", null);
        }
        try {
            return new TransitSignature(
                Integer.parseInt(matcher.group(1)), Base64.getDecoder().decode(matcher.group(2)));
        } catch (IllegalArgumentException exception) {
            throw new TransitSigningException(Reason.REJECTED, "OpenBao sign response is malformed", exception);
        }
    }

    private static TransitSigningException classify(VaultException exception) {
        String message = exception.getMessage() == null
            ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains(MIN_VERSION_REJECTION)) {
            return new TransitSigningException(
                Reason.VERSION_REJECTED, "OpenBao rejected the key version", exception);
        }
        HttpStatusCodeException http = httpCause(exception);
        if (http == null) {
            return new TransitSigningException(Reason.UNAVAILABLE, "OpenBao sign request failed", exception);
        }
        int status = http.getStatusCode().value();
        if (status == 403 || status == 429 || status >= 500) {
            return new TransitSigningException(
                Reason.UNAVAILABLE, "OpenBao is unavailable (" + status + ")", exception);
        }
        return new TransitSigningException(
            Reason.REJECTED, "OpenBao rejected the sign request (" + status + ")", exception);
    }

    private static HttpStatusCodeException httpCause(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException http) {
                return http;
            }
        }
        return null;
    }

    private static void requireSegment(String value, String name) {
        if (value == null || !PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
