package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import com.adn.dabaeum.credential.domain.TransitSigningException.Reason;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** 설계서 §7.3: key_version 을 명시하고 응답 접두어를 풀어 돌려준다. prehashed 는 보내지 않는다. */
class OpenBaoTransitSigningAdapterTest {

    private static final String MOUNT = "education-sign";
    private static final String KEY = "institution-20000000-0000-0000-0000-000000000002";
    private static final String PATH = MOUNT + "/sign/" + KEY;
    private static final byte[] INPUT = "aGVhZGVy.cGF5bG9hZA".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNATURE = new byte[64];

    private final VaultOperations vault = mock(VaultOperations.class);
    private final OpenBaoTransitSigningAdapter adapter = new OpenBaoTransitSigningAdapter(vault);

    @Test
    @SuppressWarnings("unchecked")
    void 요청은_input_과_key_version_두_필드뿐이다() {
        SIGNATURE[0] = 7;
        when(vault.write(eq(PATH), any())).thenReturn(response("vault:v3:" + base64(SIGNATURE)));

        TransitSignature signature = adapter.sign(MOUNT, KEY, 3, INPUT);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(vault).write(eq(PATH), body.capture());
        assertThat((Map<String, Object>) body.getValue())
            .containsOnlyKeys("input", "key_version")
            .containsEntry("input", Base64.getEncoder().encodeToString(INPUT))
            .containsEntry("key_version", 3);
        assertThat(signature.keyVersion()).isEqualTo(3);
        assertThat(signature.signature()).isEqualTo(SIGNATURE);
    }

    @Test
    void 응답_접두어의_버전을_고치지_않고_돌려준다() {
        when(vault.write(eq(PATH), any())).thenReturn(response("vault:v4:" + base64(SIGNATURE)));

        assertThat(adapter.sign(MOUNT, KEY, 3, INPUT).keyVersion()).isEqualTo(4);
    }

    @Test
    void 최소_버전_거부는_HTTP_500_이어도_VERSION_REJECTED_다() {
        // 실측: OpenBao 2.6.2 는 min_encryption_version 거부를 500 으로 돌려준다.
        when(vault.write(anyString(), any())).thenThrow(vaultError(HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY,
            errors("requested version for signing is less than the minimum encryption key version"),
            StandardCharsets.UTF_8)));

        assertReason(Reason.VERSION_REJECTED);
    }

    @Test
    void 봉인_권한만료_연결실패는_UNAVAILABLE_이다() {
        doThrow(vaultError(HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY,
            errors("internal error"), StandardCharsets.UTF_8))).when(vault).write(anyString(), any());
        assertReason(Reason.UNAVAILABLE);

        doThrow(vaultError(HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY,
            errors("Vault is sealed"), StandardCharsets.UTF_8))).when(vault).write(anyString(), any());
        assertReason(Reason.UNAVAILABLE);

        doThrow(vaultError(HttpClientErrorException.create(
            HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY,
            errors("permission denied"), StandardCharsets.UTF_8))).when(vault).write(anyString(), any());
        assertReason(Reason.UNAVAILABLE);

        doThrow(new ResourceAccessException("connection refused")).when(vault).write(anyString(), any());
        assertReason(Reason.UNAVAILABLE);
    }

    @Test
    void 키_없음과_잘못된_응답은_REJECTED_다() {
        doThrow(vaultError(HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
            errors("signing key not found"), StandardCharsets.UTF_8))).when(vault).write(anyString(), any());
        assertReason(Reason.REJECTED);

        doReturn(response("vault:v1:not base64!")).when(vault).write(anyString(), any());
        assertReason(Reason.REJECTED);

        doReturn(new VaultResponse()).when(vault).write(anyString(), any());
        assertReason(Reason.REJECTED);
    }

    @Test
    void 경로_조각이나_버전이_올바르지_않으면_호출하지_않는다() {
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.sign(MOUNT, "../sys", 1, INPUT));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.sign(MOUNT, KEY, 0, INPUT));
        verifyNoInteractions(vault);
    }

    private void assertReason(Reason reason) {
        assertThatThrownBy(() -> adapter.sign(MOUNT, KEY, 1, INPUT))
            .isInstanceOfSatisfying(TransitSigningException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private static VaultException vaultError(
        org.springframework.web.client.HttpStatusCodeException http
    ) {
        return VaultResponses.buildException(http, PATH);
    }

    private static byte[] errors(String message) {
        return ("{\"errors\":[\"" + message + "\"]}").getBytes(StandardCharsets.UTF_8);
    }

    private static VaultResponse response(String signature) {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("signature", signature, "key_version", 1));
        return response;
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
