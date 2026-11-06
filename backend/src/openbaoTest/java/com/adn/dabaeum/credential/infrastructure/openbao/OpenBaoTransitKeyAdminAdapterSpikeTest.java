package com.adn.dabaeum.credential.infrastructure.openbao;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

/**
 * 어댑터의 생성·재조회·회전·min_encryption_version 설정이 실 OpenBao 에서 설계값대로 동작하는지 확인한다.
 * 키 이름을 고정해 반복 실행해도 늘지 않는다.
 * 운영 mount 의 spike-* 키 (§18.3 예외, 계획 7).
 */
@EnabledIfEnvironmentVariable(named = "BAO_ADDR", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BAO_TOKEN", matches = ".+")
class OpenBaoTransitKeyAdminAdapterSpikeTest {

    private static final String MOUNT = "education-sign";
    private static final String KEY = "spike-admin-ed25519-" + Long.toHexString(System.nanoTime());

    private final VaultTemplate vault = new VaultTemplate(
        VaultEndpoint.from(URI.create(System.getenv("BAO_ADDR"))),
        new TokenAuthentication(System.getenv("BAO_TOKEN")));

    @Test
    void 생성_재조회_회전_최소_버전_설정이_설계값대로_동작한다() {
        OpenBaoTransitKeyAdminAdapter adapter = new OpenBaoTransitKeyAdminAdapter(vault);

        adapter.createKey(MOUNT, KEY);
        TransitKeyInfo created = adapter.readKey(MOUNT, KEY).orElseThrow();
        assertThat(created.matchesDesignDefaults()).isTrue();
        assertThat(created.latestVersion()).isEqualTo(1);
        assertThat(created.version(1).publicKey().getAlgorithm()).isIn("Ed25519", "EdDSA");

        adapter.createKey(MOUNT, KEY);
        assertThat(adapter.readKey(MOUNT, KEY).orElseThrow().latestVersion()).as("재생성은 버전을 늘리지 않는다").isEqualTo(1);

        adapter.rotateKey(MOUNT, KEY);
        TransitKeyInfo rotated = adapter.readKey(MOUNT, KEY).orElseThrow();
        assertThat(rotated.latestVersion()).isEqualTo(2);
        assertThat(rotated.version(2).creationTime()).isAfterOrEqualTo(rotated.version(1).creationTime());

        adapter.setMinEncryptionVersion(MOUNT, KEY, 2);
        assertThat(adapter.readKey(MOUNT, KEY).orElseThrow().minEncryptionVersion()).isEqualTo(2);
        assertThat(adapter.readKey(MOUNT, "spike-admin-missing-" + KEY)).isEmpty();
    }
}
