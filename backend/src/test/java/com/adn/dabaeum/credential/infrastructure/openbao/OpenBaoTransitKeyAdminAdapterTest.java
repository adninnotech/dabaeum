package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.VaultLoginException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class OpenBaoTransitKeyAdminAdapterTest {

    private static final String MOUNT = "education-sign";
    private static final String KEY = "institution-00000000-0000-0000-0000-000000000001";

    private final VaultOperations vault = mock(VaultOperations.class);
    private final OpenBaoTransitKeyAdminAdapter adapter = new OpenBaoTransitKeyAdminAdapter(vault);
    private KeyPair keyPair;

    @BeforeEach
    void generateKey() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void createKey_는_type_ed25519_만_보낸다() {
        adapter.createKey(MOUNT, KEY);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(vault).write(eq(MOUNT + "/keys/" + KEY), body.capture());
        assertThat(body.getValue()).isEqualTo(Map.of("type", "ed25519"));
    }

    @Test
    void readKey_는_설정_플래그_버전_공개키_생성_시각을_해석한다() {
        when(vault.read(MOUNT + "/keys/" + KEY)).thenReturn(response(keyResponse(rawBase64(), 2, 1, 0)));

        TransitKeyInfo info = adapter.readKey(MOUNT, KEY).orElseThrow();

        assertThat(info.type()).isEqualTo("ed25519");
        assertThat(info.exportable()).isFalse();
        assertThat(info.latestVersion()).isEqualTo(2);
        assertThat(info.minEncryptionVersion()).isEqualTo(1);
        assertThat(info.autoRotatePeriodSeconds()).isZero();
        assertThat(info.matchesDesignDefaults()).isTrue();
        assertThat(info.versions()).containsOnlyKeys(1, 2);
        assertThat(info.version(2).publicKey().getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(info.version(2).creationTime()).isEqualTo(Instant.parse("2026-09-11T01:02:03.123456789Z"));
    }

    @Test
    void readKey_는_PEM_공개키도_받고_설계값이_아니면_matchesDesignDefaults_가_거짓이다() {
        Map<String, Object> data = keyResponse(pem(), 1, 0, 86400);
        data.put("exportable", true);
        when(vault.read(MOUNT + "/keys/" + KEY)).thenReturn(response(data));

        TransitKeyInfo info = adapter.readKey(MOUNT, KEY).orElseThrow();

        assertThat(info.version(1).publicKey().getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(info.matchesDesignDefaults()).isFalse();
        assertThat(info.autoRotatePeriodSeconds()).isEqualTo(86400);
    }

    @Test
    void readKey_는_키가_없으면_empty_이고_응답이_깨지면_REJECTED_다() {
        when(vault.read(MOUNT + "/keys/missing")).thenReturn(null);
        assertThat(adapter.readKey(MOUNT, "missing")).isEqualTo(Optional.empty());

        Map<String, Object> broken = keyResponse(rawBase64(), 1, 0, 0);
        broken.put("keys", Map.of("1", Map.of("public_key", "not-base64!", "creation_time", "x")));
        when(vault.read(MOUNT + "/keys/" + KEY)).thenReturn(response(broken));
        assertThatThrownBy(() -> adapter.readKey(MOUNT, KEY))
            .isInstanceOf(TransitKeyAdminException.class)
            .extracting(exception -> ((TransitKeyAdminException) exception).reason())
            .isEqualTo(TransitKeyAdminException.Reason.REJECTED);
    }

    @Test
    void rotate_와_config_는_Policy_가_허용하는_경로와_파라미터만_쓴다() {
        adapter.rotateKey(MOUNT, KEY);
        adapter.setMinEncryptionVersion(MOUNT, KEY, 3);

        verify(vault).write(MOUNT + "/keys/" + KEY + "/rotate");
        verify(vault).write(MOUNT + "/keys/" + KEY + "/config", Map.of("min_encryption_version", 3));
        assertThatThrownBy(() -> adapter.setMinEncryptionVersion(MOUNT, KEY, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 오류는_서명_어댑터와_같은_기준으로_나뉜다() {
        when(vault.write(eq(MOUNT + "/keys/a/rotate")))
            .thenThrow(new VaultException("boom", new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)));
        when(vault.write(eq(MOUNT + "/keys/b/rotate")))
            .thenThrow(new VaultException("boom", new HttpClientErrorException(HttpStatus.FORBIDDEN)));
        when(vault.write(eq(MOUNT + "/keys/c/rotate")))
            .thenThrow(new VaultException("boom", new HttpClientErrorException(HttpStatus.BAD_REQUEST)));
        when(vault.write(eq(MOUNT + "/keys/d/rotate"))).thenThrow(new ResourceAccessException("down"));

        assertThat(reason(() -> adapter.rotateKey(MOUNT, "a"))).isEqualTo(TransitKeyAdminException.Reason.UNAVAILABLE);
        assertThat(reason(() -> adapter.rotateKey(MOUNT, "b"))).isEqualTo(TransitKeyAdminException.Reason.UNAVAILABLE);
        assertThat(reason(() -> adapter.rotateKey(MOUNT, "c"))).isEqualTo(TransitKeyAdminException.Reason.REJECTED);
        assertThat(reason(() -> adapter.rotateKey(MOUNT, "d"))).isEqualTo(TransitKeyAdminException.Reason.UNAVAILABLE);
        assertThatThrownBy(() -> adapter.createKey("bad mount", KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 로그인_실패는_키_거부와_따로_AUTHENTICATION_FAILED_다() {
        // AppRole Secret ID 가 만료되면 로그인이 400 이다. 키를 거부한 것이 아니라 OpenBao 에 닿지 못한 것이다(QA INST-03).
        when(vault.write(eq(MOUNT + "/keys/e/rotate"))).thenThrow(new VaultLoginException(
            "Cannot login using AppRole", new HttpClientErrorException(HttpStatus.BAD_REQUEST)));

        assertThat(reason(() -> adapter.rotateKey(MOUNT, "e")))
            .isEqualTo(TransitKeyAdminException.Reason.AUTHENTICATION_FAILED);
    }

    private static TransitKeyAdminException.Reason reason(Runnable call) {
        try {
            call.run();
            throw new AssertionError("expected TransitKeyAdminException");
        } catch (TransitKeyAdminException exception) {
            return exception.reason();
        }
    }

    private Map<String, Object> keyResponse(String publicKey, int latest, int minEncryption, long autoRotate) {
        Map<String, Object> versions = new HashMap<>();
        for (int version = 1; version <= latest; version++) {
            versions.put(String.valueOf(version), Map.of(
                "public_key", publicKey,
                "creation_time", "2026-09-11T01:02:03.123456789Z"));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", "ed25519");
        data.put("exportable", false);
        data.put("allow_plaintext_backup", false);
        data.put("deletion_allowed", false);
        data.put("derived", false);
        data.put("latest_version", latest);
        data.put("min_encryption_version", minEncryption);
        data.put("auto_rotate_period", autoRotate);
        data.put("keys", versions);
        return data;
    }

    private String rawBase64() {
        byte[] encoded = keyPair.getPublic().getEncoded();
        return Base64.getEncoder().encodeToString(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }

    private String pem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    private static VaultResponse response(Map<String, Object> data) {
        VaultResponse response = new VaultResponse();
        response.setData(data);
        return response;
    }
}
